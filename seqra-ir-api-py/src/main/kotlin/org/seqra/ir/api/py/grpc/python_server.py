import json
import grpc
from concurrent import futures
from pathlib import Path
import logging
import traceback

from google.protobuf.json_format import MessageToDict
from mypyc.ir.ops import Integer, Op, Goto, Branch, Return

import ir_pb2
import ir_pb2_grpc

from mypyc.analysis import dataflow
from mypyc.ir.class_ir import ClassIR as MypyClassIR
from mypyc.ir.module_ir import ModuleIR
from mypyc.ir.func_ir import FuncIR, BasicBlock, Register, Assign
from mypyc.common import TOP_LEVEL_NAME
from mypyc.transform import exceptions

from ir_representation import get_modules, get_cfg

logging.basicConfig(level=logging.DEBUG)
logger = logging.getLogger(__name__)


def parse_source(request):
    if request.HasField("files"):
        return {"files": list(request.files.files)}
    elif request.HasField("directory"):
        return {"dir": request.directory.path}
    else:
        raise ValueError("SourceRequest must contain files or directory")


def convert_module(module: ModuleIR) -> ir_pb2.Module:
    final_names = []
    if hasattr(module, 'final_names'):
        for name, rtype in module.final_names:
            rtype_proto = convert_rtype(rtype)
            final_names.append(ir_pb2.StringRtype(
                name=str(name),
                type=rtype_proto
            ))

    return ir_pb2.Module(
        fullname=module.fullname,
        imports=list(module.imports),
        functions=[convert_function(f) for f in module.functions],
        classes=[convert_class(c) for c in module.classes.values()],
        final_names=final_names,
        type_var_names=list(module.type_var_names) if hasattr(module, 'type_var_names') else []
    )


def convert_rtype(rtype) -> ir_pb2.RType:
    if rtype is None:
        return ir_pb2.RType(name="None")

    rtype_proto = ir_pb2.RType(
        name=str(rtype),
        is_unboxed=getattr(rtype, 'is_unboxed', False),
        c_undefined=getattr(rtype, 'c_undefined', ''),
        is_refcounted=getattr(rtype, 'is_refcounted', False),
        ctype=getattr(rtype, '_ctype', ''),
        error_overlap=getattr(rtype, 'error_overlap', False)
    )

    if hasattr(rtype, '__class__'):
        class_name = rtype.__class__.__name__
        if class_name == 'RPrimitive':
            rprimitive = ir_pb2.RPrimitive(
                is_native_int=getattr(rtype, 'is_native_int', False),
                is_signed=getattr(rtype, 'is_signed', False),
                size=getattr(rtype, 'size', 0),
                may_be_immortal=getattr(rtype, 'may_be_immortal', False)
            )
            rtype_proto.rprimitive.CopyFrom(rprimitive)

        elif class_name == 'RTuple':
            rtuple = ir_pb2.RTuple(
                unique_id=getattr(rtype, 'unique_id', ''),
                struct_name=getattr(rtype, 'struct_name', '')
            )
            if hasattr(rtype, 'types'):
                for t in rtype.types:
                    rtuple.types.append(convert_rtype(t))
            rtype_proto.rtuple.CopyFrom(rtuple)

        elif class_name == 'RStruct':
            rstruct = ir_pb2.RStruct()
            if hasattr(rtype, 'names'):
                rstruct.names.extend(rtype.names)
            if hasattr(rtype, 'types'):
                for t in rtype.types:
                    rstruct.types.append(convert_rtype(t))
            if hasattr(rtype, 'offsets'):
                rstruct.offsets.extend(rtype.offsets)
            if hasattr(rtype, 'size'):
                rstruct.size = rtype.size
            rtype_proto.rstruct.CopyFrom(rstruct)

        elif class_name == 'RVoid':
            rtype_proto.rvoid.CopyFrom(ir_pb2.RVoid())

        elif class_name == 'RInstance':
            rtype_proto.rinstance.CopyFrom(ir_pb2.RInstance())

    return rtype_proto


def convert_register(reg: Register) -> ir_pb2.Register:
    return ir_pb2.Register(
        name=reg.name,
        type=convert_rtype(reg.type),
        is_arg=reg.is_arg,
        is_borrowed=reg.is_borrowed,
        line=reg.line
    )


def convert_value(value) -> ir_pb2.Value:
    value_proto = ir_pb2.Value(
        line=value.line,
        type=convert_rtype(getattr(value, 'type', None)),
        is_borrowed=getattr(value, 'is_borrowed', False),
    )

    if isinstance(value, Register):
        register_proto = convert_register(value)
        value_proto.register.CopyFrom(register_proto)
    elif isinstance(value, Integer):
        value_proto.integer = ir_pb2.Integer(
            value=value.value,
            type=convert_rtype(value.type),
            line=value.line
        )

    return value_proto


def convert_func_decl(decl) -> ir_pb2.FuncDecl:
    if not decl:
        return None

    func_decl_proto = ir_pb2.FuncDecl()

    try:
        func_decl_proto.name = str(getattr(decl, 'name', ''))

        if hasattr(decl, 'class_name'):
            class_name = decl.class_name
            if class_name is not None:
                func_decl_proto.class_name = str(class_name)

        func_decl_proto.module_name = str(getattr(decl, 'module_name', ''))

        if hasattr(decl, 'kind'):
            kind_value = decl.kind
            if hasattr(kind_value, 'value'):
                kind_value = kind_value.value

            kind_mapping = {
                0: ir_pb2.FunctionKind.FUNC_UNSPECIFIED,
                1: ir_pb2.FunctionKind.FUNC_NORMAL,
                2: ir_pb2.FunctionKind.FUNC_STATICMETHOD,
                3: ir_pb2.FunctionKind.FUNC_CLASSMETHOD,
                4: ir_pb2.FunctionKind.FUNC_PROPERTY_GETTER,
                5: ir_pb2.FunctionKind.FUNC_PROPERTY_SETTER,
            }
            func_decl_proto.kind = kind_mapping.get(kind_value, ir_pb2.FunctionKind.FUNC_UNSPECIFIED)

        func_decl_proto.is_prop_setter = bool(getattr(decl, 'is_prop_setter', False))
        func_decl_proto.is_prop_getter = bool(getattr(decl, 'is_prop_getter', False))
        func_decl_proto.is_generator = bool(getattr(decl, 'is_generator', False))
        func_decl_proto.is_coroutine = bool(getattr(decl, 'is_coroutine', False))
        func_decl_proto.implicit = bool(getattr(decl, 'implicit', False))
        func_decl_proto.internal = bool(getattr(decl, 'internal', False))

        if hasattr(decl, 'line'):
            line_val = decl.line
            if line_val is not None:
                func_decl_proto.line = int(line_val)

        if hasattr(decl, 'sig'):
            sig = decl.sig
            sig_proto = ir_pb2.FuncSignature()

            if hasattr(sig, 'args'):
                for arg_type in sig.args:
                    arg_type_proto = convert_rtype(arg_type)
                    sig_proto.args.append(arg_type_proto)

            if hasattr(sig, 'ret_type'):
                ret_type_proto = convert_rtype(sig.ret_type)
                sig_proto.ret_type.CopyFrom(ret_type_proto)

            sig_proto.num_bitmap_args = int(getattr(sig, 'num_bitmap_args', 0))

            func_decl_proto.sig.CopyFrom(sig_proto)

        if hasattr(decl, 'bound_sig'):
            bound_sig = decl.bound_sig
            if bound_sig:
                bound_sig_proto = ir_pb2.FuncSignature()

                if hasattr(bound_sig, 'args'):
                    for arg_type in bound_sig.args:
                        arg_type_proto = convert_rtype(arg_type)
                        bound_sig_proto.args.append(arg_type_proto)

                if hasattr(bound_sig, 'ret_type'):
                    ret_type_proto = convert_rtype(bound_sig.ret_type)
                    bound_sig_proto.ret_type.CopyFrom(ret_type_proto)

                bound_sig_proto.num_bitmap_args = int(getattr(bound_sig, 'num_bitmap_args', 0))

                func_decl_proto.bound_sig.CopyFrom(bound_sig_proto)

    except Exception as e:
        logger.warning(f"Error converting FuncDecl: {e}")
        logger.debug(traceback.format_exc())

    return func_decl_proto


def convert_func_signature(sig) -> ir_pb2.FuncSignature:
    if not sig:
        return None

    sig_proto = ir_pb2.FuncSignature()

    try:
        if hasattr(sig, 'args'):
            for arg_type in sig.args:
                arg_type_proto = convert_rtype(arg_type)
                sig_proto.args.append(arg_type_proto)

        if hasattr(sig, 'ret_type'):
            ret_type_proto = convert_rtype(sig.ret_type)
            sig_proto.ret_type.CopyFrom(ret_type_proto)

        sig_proto.num_bitmap_args = int(getattr(sig, 'num_bitmap_args', 0))

    except Exception as e:
        logger.warning(f"Error converting FuncSignature: {e}")

    return sig_proto


def convert_op(op: Op) -> ir_pb2.Op:
    op_proto = ir_pb2.Op(value=convert_value(op))

    try:
        op_class_name = op.__class__.__name__
        logger.debug(f"Converting operation: {op_class_name}")
        op_proto.name = op_class_name

        if op_class_name == 'Assign':
            base_assign = ir_pb2.BaseAssign()

            if hasattr(op, 'dest'):
                base_assign.dest.CopyFrom(convert_register(op.dest))

            if hasattr(op, 'src'):
                assign = ir_pb2.Assign()
                assign.src.CopyFrom(convert_value(op.src))
                base_assign.assign.CopyFrom(assign)

            op_proto.base_assing.CopyFrom(base_assign)

        elif op_class_name == 'AssignMulti':
            base_assign = ir_pb2.BaseAssign()

            if hasattr(op, 'dest'):
                base_assign.dest.CopyFrom(convert_register(op.dest))

            if hasattr(op, 'src'):
                assign_multi = ir_pb2.AssignMulti()
                for src in op.src:
                    assign_multi.src.append(convert_value(src))
                base_assign.assing_multi.CopyFrom(assign_multi)

            op_proto.base_assing.CopyFrom(base_assign)

        elif op_class_name == 'Goto':
            control_op = ir_pb2.ControlOp()
            goto = ir_pb2.Goto(label=op.label)
            control_op.goto.CopyFrom(goto)
            op_proto.control_op.CopyFrom(control_op)

        elif op_class_name == 'Branch':
            control_op = ir_pb2.ControlOp()
            branch = ir_pb2.Branch()

            if hasattr(op, 'value'):
                branch.value.CopyFrom(convert_value(op.value))
            if hasattr(op, 'true'):
                branch.true_label.CopyFrom(ir_pb2.BasicBlock(label=op.true.label))
            if hasattr(op, 'false'):
                branch.false_label.CopyFrom(ir_pb2.BasicBlock(label=op.false.label))

            if hasattr(op, 'op'):
                if op.op == 'is_error':
                    branch.op = ir_pb2.Branch.OpType.IS_ERROR
                else:
                    branch.op = ir_pb2.Branch.OpType.BOOL

            branch.negated = getattr(op, 'negated', False)
            branch.rare = getattr(op, 'rare', False)

            if hasattr(op, 'traceback_entry'):
                if op.traceback_entry:
                    traceback = ir_pb2.TracebackEntry(
                        func_name=getattr(op.traceback_entry, 'func_name', ''),
                        line=getattr(op.traceback_entry, 'line', 0)
                    )
                    branch.traceback_entry.CopyFrom(traceback)

            control_op.branch.CopyFrom(branch)
            op_proto.control_op.CopyFrom(control_op)

        elif op_class_name == 'Return':
            control_op = ir_pb2.ControlOp()
            return_op = ir_pb2.Return()

            if hasattr(op, 'value'):
                return_op.value.CopyFrom(convert_value(op.value))
            if hasattr(op, 'yield_target'):
                return_op.yield_target = op.yield_target

            control_op.return_.CopyFrom(return_op)
            op_proto.control_op.CopyFrom(control_op)

        elif op_class_name == 'Unreachable':
            control_op = ir_pb2.ControlOp()
            control_op.unreachable.CopyFrom(ir_pb2.Unreachable())
            op_proto.control_op.CopyFrom(control_op)

        elif op_class_name == 'IncRef':
            register_op = ir_pb2.RegisterOp()
            inc_ref = ir_pb2.IncRef()
            if hasattr(op, 'src'):
                inc_ref.src.CopyFrom(convert_value(op.src))
            register_op.inc_ref.CopyFrom(inc_ref)
            op_proto.register_op.CopyFrom(register_op)

        elif op_class_name == 'DecRef':
            register_op = ir_pb2.RegisterOp()
            dec_ref = ir_pb2.DecRef()
            if hasattr(op, 'src'):
                dec_ref.src.CopyFrom(convert_value(op.src))
            dec_ref.is_xdec = getattr(op, 'is_xdec', False)
            register_op.dec_ref.CopyFrom(dec_ref)
            op_proto.register_op.CopyFrom(register_op)

        elif op_class_name == 'Call':
            try:
                register_op = ir_pb2.RegisterOp()
                call = ir_pb2.Call()

                error_kind = getattr(op, 'error_kind', None)
                if error_kind:
                    if hasattr(error_kind, 'value'):
                        error_kind_value = error_kind.value
                    else:
                        error_kind_value = error_kind

                    error_kind_mapping = {
                        0: ir_pb2.ERR_NEVER,
                        1: ir_pb2.ERR_MAGIC,
                        2: ir_pb2.ERR_FALSE,
                        3: ir_pb2.ERR_ALWAYS,
                        4: ir_pb2.ERR_MAGIC_OVERLAPPING,
                    }
                    register_op.error_kind = error_kind_mapping.get(int(error_kind_value), ir_pb2.ERR_NEVER)
                else:
                    register_op.error_kind = ir_pb2.ERR_NEVER

                if hasattr(op, 'fn'):
                    fn_decl = op.fn
                    if fn_decl:
                        fn_decl_proto = convert_func_decl(fn_decl)
                        if fn_decl_proto:
                            call.fn.CopyFrom(fn_decl_proto)

                if hasattr(op, 'args'):
                    for arg in op.args:
                        if arg:
                            arg_proto = convert_value(arg)
                            if arg_proto:
                                call.args.append(arg_proto)

                if hasattr(op, 'type'):
                    type_proto = convert_rtype(op.type)
                    if type_proto:
                        call.type.CopyFrom(type_proto)

                register_op.call.CopyFrom(call)
                op_proto.register_op.CopyFrom(register_op)

            except Exception as e:
                logger.warning(f"Error converting Call operation: {e}")

        elif op_class_name == 'MethodCall':
            register_op = ir_pb2.RegisterOp()
            method_call = ir_pb2.MethodCall()
            register_op.error_kind = ir_pb2.ERR_NEVER
            if hasattr(op, 'obj'):
                obj_proto = convert_value(op.obj)
                method_call.obj.CopyFrom(obj_proto)

            method_call.method = str(getattr(op, 'method', ''))
            if hasattr(op, 'args'):

                for arg in op.args:
                    arg_proto = convert_value(arg)
                    method_call.args.append(arg_proto)

            if hasattr(op, 'receiver_type'):
                receiver_type_proto = convert_rtype(op.receiver_type)
                method_call.receiver_type.CopyFrom(receiver_type_proto)

            if hasattr(op, 'type'):
                type_proto = convert_rtype(op.type)
                method_call.type.CopyFrom(type_proto)

            register_op.method_call.CopyFrom(method_call)
            op_proto.register_op.CopyFrom(register_op)

        elif op_class_name == 'PrimitiveOp':
            register_op = ir_pb2.RegisterOp()
            primitive_op = ir_pb2.PrimitiveOp()

            if hasattr(op, 'args'):
                for arg in op.args:
                    primitive_op.args.append(convert_value(arg))

            if hasattr(op, 'type'):
                primitive_op.type.CopyFrom(convert_rtype(op.type))

            if hasattr(op, 'desc'):
                pass

            register_op.primitive_op.CopyFrom(primitive_op)
            op_proto.register_op.CopyFrom(register_op)

        elif op_class_name == 'LoadErrorValue':
            register_op = ir_pb2.RegisterOp()
            load_error = ir_pb2.LoadErrorValue()

            if hasattr(op, 'type'):
                load_error.type.CopyFrom(convert_rtype(op.type))

            load_error.is_borrowed = getattr(op, 'is_borrowed', False)
            load_error.undefines = getattr(op, 'undefines', False)

            register_op.load_error_value.CopyFrom(load_error)
            op_proto.register_op.CopyFrom(register_op)

        elif op_class_name == 'LoadLiteral':
            register_op = ir_pb2.RegisterOp()
            load_literal = ir_pb2.LoadLiteral()

            if hasattr(op, 'type'):
                load_literal.type.CopyFrom(convert_rtype(op.type))

            if hasattr(op, 'value'):
                literal = ir_pb2.LiteralValue()
                if isinstance(op.value, int):
                    literal.int_value = op.value
                elif isinstance(op.value, str):
                    literal.str_value = op.value
                elif isinstance(op.value, bool):
                    literal.bool_value = op.value
                elif isinstance(op.value, float):
                    literal.float_value = op.value
                load_literal.value.CopyFrom(literal)

            register_op.load_literal.CopyFrom(load_literal)
            op_proto.register_op.CopyFrom(register_op)

        elif op_class_name == 'GetAttr':
            register_op = ir_pb2.RegisterOp()
            get_attr = ir_pb2.GetAttr()

            if hasattr(op, 'obj'):
                get_attr.obj.CopyFrom(convert_value(op.obj))

            get_attr.attr = getattr(op, 'attr', '')
            get_attr.allow_error_value = getattr(op, 'allow_error_value', False)

            if hasattr(op, 'class_type'):
                get_attr.class_type.CopyFrom(convert_rtype(op.class_type))

            get_attr.is_borrowed = getattr(op, 'is_borrowed', False)

            register_op.get_attr.CopyFrom(get_attr)
            op_proto.register_op.CopyFrom(register_op)

        elif op_class_name == 'SetAttr':
            register_op = ir_pb2.RegisterOp()
            set_attr = ir_pb2.SetAttr()

            if hasattr(op, 'obj'):
                set_attr.obj.CopyFrom(convert_value(op.obj))

            set_attr.attr = getattr(op, 'attr', '')

            if hasattr(op, 'src'):
                set_attr.src.CopyFrom(convert_value(op.src))

            if hasattr(op, 'class_type'):
                set_attr.class_type.CopyFrom(convert_rtype(op.class_type))

            if hasattr(op, 'type'):
                set_attr.type.CopyFrom(convert_rtype(op.type))

            set_attr.is_init = getattr(op, 'is_init', False)

            register_op.set_attr.CopyFrom(set_attr)
            op_proto.register_op.CopyFrom(register_op)

        elif op_class_name == 'LoadStatic':
            register_op = ir_pb2.RegisterOp()
            load_static = ir_pb2.LoadStatic()

            load_static.identifier = getattr(op, 'identifier', '')
            load_static.module_name = getattr(op, 'module_name', '')
            load_static.namespace = getattr(op, 'namespace', '')

            if hasattr(op, 'type'):
                load_static.type.CopyFrom(convert_rtype(op.type))

            if hasattr(op, 'ann'):
                import pickle
                try:
                    load_static.ann = pickle.dumps(op.ann)
                except:
                    load_static.ann = b''

            register_op.load_static.CopyFrom(load_static)
            op_proto.register_op.CopyFrom(register_op)

        elif op_class_name == 'InitStatic':
            register_op = ir_pb2.RegisterOp()
            init_static = ir_pb2.InitStatic()

            init_static.identifier = getattr(op, 'identifier', '')
            init_static.module_name = getattr(op, 'module_name', '')
            init_static.namespace = getattr(op, 'namespace', '')

            if hasattr(op, 'value'):
                init_static.value.CopyFrom(convert_value(op.value))

            register_op.init_static.CopyFrom(init_static)
            op_proto.register_op.CopyFrom(register_op)

        elif op_class_name == 'TupleSet':
            register_op = ir_pb2.RegisterOp()
            tuple_set = ir_pb2.TupleSet()

            if hasattr(op, 'items'):
                for item in op.items:
                    tuple_set.items.append(convert_value(item))

            if hasattr(op, 'tuple_type'):
                tuple_set.tuple_type.CopyFrom(convert_rtype(op.tuple_type))

            if hasattr(op, 'type'):
                tuple_set.type.CopyFrom(convert_rtype(op.type))

            register_op.tuple_set.CopyFrom(tuple_set)
            op_proto.register_op.CopyFrom(register_op)

        elif op_class_name == 'TupleGet':
            register_op = ir_pb2.RegisterOp()
            tuple_get = ir_pb2.TupleGet()

            if hasattr(op, 'src'):
                tuple_get.src.CopyFrom(convert_value(op.src))

            tuple_get.index = getattr(op, 'index', 0)

            if hasattr(op, 'type'):
                tuple_get.type.CopyFrom(convert_rtype(op.type))

            tuple_get.is_borrowed = getattr(op, 'is_borrowed', False)

            register_op.tuple_get.CopyFrom(tuple_get)
            op_proto.register_op.CopyFrom(register_op)

        elif op_class_name == 'Cast':
            register_op = ir_pb2.RegisterOp()
            cast = ir_pb2.Cast()

            if hasattr(op, 'src'):
                cast.src.CopyFrom(convert_value(op.src))

            if hasattr(op, 'type'):
                cast.type.CopyFrom(convert_rtype(op.type))

            cast.is_borrowed = getattr(op, 'is_borrowed', False)
            cast.is_unchecked = getattr(op, 'is_unchecked', False)

            register_op.cast.CopyFrom(cast)
            op_proto.register_op.CopyFrom(register_op)

        elif op_class_name == 'Box':
            register_op = ir_pb2.RegisterOp()
            box = ir_pb2.Box()

            if hasattr(op, 'src'):
                box.src.CopyFrom(convert_value(op.src))

            if hasattr(op, 'type'):
                box.type.CopyFrom(convert_rtype(op.type))

            box.is_borrowed = getattr(op, 'is_borrowed', False)

            register_op.box.CopyFrom(box)
            op_proto.register_op.CopyFrom(register_op)

        elif op_class_name == 'Unbox':
            register_op = ir_pb2.RegisterOp()
            unbox = ir_pb2.Unbox()

            if hasattr(op, 'src'):
                unbox.src.CopyFrom(convert_value(op.src))

            if hasattr(op, 'type'):
                unbox.type.CopyFrom(convert_rtype(op.type))

            register_op.unbox.CopyFrom(unbox)
            op_proto.register_op.CopyFrom(register_op)

        elif op_class_name == 'RaiseStandardError':
            register_op = ir_pb2.RegisterOp()
            raise_error = ir_pb2.RaiseStandardError()

            raise_error.class_name = getattr(op, 'class_name', '')

            if hasattr(op, 'value'):
                if isinstance(op.value, str):
                    raise_error.str_value = op.value
                else:
                    raise_error.value.CopyFrom(convert_value(op.value))

            if hasattr(op, 'type'):
                raise_error.type.CopyFrom(convert_rtype(op.type))

            register_op.raise_standard_error.CopyFrom(raise_error)
            op_proto.register_op.CopyFrom(register_op)

        elif op_class_name == 'CallC':
            register_op = ir_pb2.RegisterOp()
            call_c = ir_pb2.CallC()

            call_c.function_name = getattr(op, 'function_name', '')

            if hasattr(op, 'args'):
                for arg in op.args:
                    call_c.args.append(convert_value(arg))

            if hasattr(op, 'type'):
                call_c.type.CopyFrom(convert_rtype(op.type))

            if hasattr(op, 'steals'):
                pass

            call_c.is_borrowed = getattr(op, 'is_borrowed', False)
            call_c.var_arg_idx = getattr(op, 'var_arg_idx', 0)
            call_c.is_pure = getattr(op, 'is_pure', False)
            call_c.returns_null = getattr(op, 'returns_null', False)

            if hasattr(op, 'dependencies'):
                pass

            register_op.call_c.CopyFrom(call_c)
            op_proto.register_op.CopyFrom(register_op)

        elif op_class_name == 'Truncate':
            register_op = ir_pb2.RegisterOp()
            truncate = ir_pb2.Truncate()

            if hasattr(op, 'src'):
                truncate.src.CopyFrom(convert_value(op.src))

            if hasattr(op, 'type'):
                truncate.type.CopyFrom(convert_rtype(op.type))

            if hasattr(op, 'src_type'):
                truncate.src_type.CopyFrom(convert_rtype(op.src_type))

            register_op.truncate.CopyFrom(truncate)
            op_proto.register_op.CopyFrom(register_op)

        elif op_class_name == 'Extend':
            register_op = ir_pb2.RegisterOp()
            extend = ir_pb2.Extend()

            if hasattr(op, 'src'):
                extend.src.CopyFrom(convert_value(op.src))

            if hasattr(op, 'type'):
                extend.type.CopyFrom(convert_rtype(op.type))

            if hasattr(op, 'src_type'):
                extend.src_type.CopyFrom(convert_rtype(op.src_type))

            extend.signed = getattr(op, 'signed', False)

            register_op.extend.CopyFrom(extend)
            op_proto.register_op.CopyFrom(register_op)

        elif op_class_name == 'LoadGlobal':
            register_op = ir_pb2.RegisterOp()
            load_global = ir_pb2.LoadGlobal()

            load_global.identifier = getattr(op, 'identifier', '')

            if hasattr(op, 'type'):
                load_global.type.CopyFrom(convert_rtype(op.type))

            if hasattr(op, 'ann'):
                import pickle
                try:
                    load_global.ann = pickle.dumps(op.ann)
                except:
                    load_global.ann = b''

            register_op.load_global.CopyFrom(load_global)
            op_proto.register_op.CopyFrom(register_op)

        elif op_class_name == 'IntOp':
            register_op = ir_pb2.RegisterOp()
            int_op = ir_pb2.IntOp()

            if hasattr(op, 'type'):
                int_op.type.CopyFrom(convert_rtype(op.type))

            if hasattr(op, 'lhs'):
                int_op.lhs.CopyFrom(convert_value(op.lhs))

            if hasattr(op, 'rhs'):
                int_op.rhs.CopyFrom(convert_value(op.rhs))

            if hasattr(op, 'op'):
                op_mapping = {
                    0: ir_pb2.IntOp.OpType.ADD,
                    1: ir_pb2.IntOp.OpType.SUB,
                    2: ir_pb2.IntOp.OpType.MUL,
                    3: ir_pb2.IntOp.OpType.DIV,
                    4: ir_pb2.IntOp.OpType.MOD,
                    200: ir_pb2.IntOp.OpType.AND,
                    201: ir_pb2.IntOp.OpType.OR,
                    202: ir_pb2.IntOp.OpType.XOR,
                    203: ir_pb2.IntOp.OpType.LEFT_SHIFT,
                    204: ir_pb2.IntOp.OpType.RIGHT_SHIFT
                }
                int_op.op = op_mapping.get(op.op, ir_pb2.IntOp.OpType.ADD)

            register_op.int_op.CopyFrom(int_op)
            op_proto.register_op.CopyFrom(register_op)

        elif op_class_name == 'ComparisonOp':
            register_op = ir_pb2.RegisterOp()
            comparison_op = ir_pb2.ComparisonOp()

            if hasattr(op, 'type'):
                comparison_op.type.CopyFrom(convert_rtype(op.type))

            if hasattr(op, 'lhs'):
                comparison_op.lhs.CopyFrom(convert_value(op.lhs))

            if hasattr(op, 'rhs'):
                comparison_op.rhs.CopyFrom(convert_value(op.rhs))

            if hasattr(op, 'op'):
                op_mapping = {
                    '==': ir_pb2.ComparisonOp.OpType.EQ,
                    '!=': ir_pb2.ComparisonOp.OpType.NEQ,
                    '<': ir_pb2.ComparisonOp.OpType.SLT,
                    '>': ir_pb2.ComparisonOp.OpType.SGT,
                    '<=': ir_pb2.ComparisonOp.OpType.SLE,
                    '>=': ir_pb2.ComparisonOp.OpType.SGE,
                    '<U': ir_pb2.ComparisonOp.OpType.ULT,
                    '>U': ir_pb2.ComparisonOp.OpType.UGT,
                    '<=U': ir_pb2.ComparisonOp.OpType.ULE,
                    '>=U': ir_pb2.ComparisonOp.OpType.UGE
                }
                comparison_op.op = op_mapping.get(op.op, ir_pb2.ComparisonOp.OpType.Default)

            register_op.comparison_op.CopyFrom(comparison_op)
            op_proto.register_op.CopyFrom(register_op)

        elif op_class_name == 'FloatOp':
            register_op = ir_pb2.RegisterOp()
            float_op = ir_pb2.FloatOp()

            if hasattr(op, 'type'):
                float_op.type.CopyFrom(convert_rtype(op.type))

            if hasattr(op, 'lhs'):
                float_op.lhs.CopyFrom(convert_value(op.lhs))

            if hasattr(op, 'rhs'):
                float_op.rhs.CopyFrom(convert_value(op.rhs))

            if hasattr(op, 'op'):
                op_mapping = {
                    0: ir_pb2.FloatOp.OpType.ADD,
                    1: ir_pb2.FloatOp.OpType.SUB,
                    2: ir_pb2.FloatOp.OpType.MUL,
                    3: ir_pb2.FloatOp.OpType.DIV,
                    4: ir_pb2.FloatOp.OpType.MOD
                }
                float_op.op = op_mapping.get(op.op, ir_pb2.FloatOp.OpType.ADD)

            register_op.float_op.CopyFrom(float_op)
            op_proto.register_op.CopyFrom(register_op)

        elif op_class_name == 'FloatNeg':
            register_op = ir_pb2.RegisterOp()
            float_neg = ir_pb2.FloatNeg()

            if hasattr(op, 'type'):
                float_neg.type.CopyFrom(convert_rtype(op.type))

            if hasattr(op, 'src'):
                float_neg.src.CopyFrom(convert_value(op.src))

            register_op.float_neg.CopyFrom(float_neg)
            op_proto.register_op.CopyFrom(register_op)

        elif op_class_name == 'FloatComparisonOp':
            register_op = ir_pb2.RegisterOp()
            float_comparison_op = ir_pb2.FloatComparisonOp()

            if hasattr(op, 'type'):
                float_comparison_op.type.CopyFrom(convert_rtype(op.type))

            if hasattr(op, 'lhs'):
                float_comparison_op.lhs.CopyFrom(convert_value(op.lhs))

            if hasattr(op, 'rhs'):
                float_comparison_op.rhs.CopyFrom(convert_value(op.rhs))

            if hasattr(op, 'op'):
                op_mapping = {
                    '==': ir_pb2.FloatComparisonOp.OpType.EQ,
                    '!=': ir_pb2.FloatComparisonOp.OpType.NEQ,
                    '<': ir_pb2.FloatComparisonOp.OpType.LT,
                    '>': ir_pb2.FloatComparisonOp.OpType.GT,
                    '<=': ir_pb2.FloatComparisonOp.OpType.LE,
                    '>=': ir_pb2.FloatComparisonOp.OpType.GE
                }
                float_comparison_op.op = op_mapping.get(op.op, ir_pb2.FloatComparisonOp.OpType.Default)

            register_op.float_comparison_op.CopyFrom(float_comparison_op)
            op_proto.register_op.CopyFrom(register_op)

        elif op_class_name == 'LoadMem':
            register_op = ir_pb2.RegisterOp()
            load_mem = ir_pb2.LoadMem()

            if hasattr(op, 'type'):
                load_mem.type.CopyFrom(convert_rtype(op.type))

            if hasattr(op, 'src'):
                load_mem.src.CopyFrom(convert_value(op.src))

            load_mem.is_borrowed = getattr(op, 'is_borrowed', False)

            register_op.load_mem.CopyFrom(load_mem)
            op_proto.register_op.CopyFrom(register_op)

        elif op_class_name == 'SetMem':
            register_op = ir_pb2.RegisterOp()
            set_mem = ir_pb2.SetMem()

            if hasattr(op, 'type'):
                set_mem.type.CopyFrom(convert_rtype(op.type))

            if hasattr(op, 'dest_type'):
                set_mem.dest_type.CopyFrom(convert_rtype(op.dest_type))

            if hasattr(op, 'src'):
                set_mem.src.CopyFrom(convert_value(op.src))

            if hasattr(op, 'dest'):
                set_mem.dest.CopyFrom(convert_value(op.dest))

            register_op.set_mem.CopyFrom(set_mem)
            op_proto.register_op.CopyFrom(register_op)

        elif op_class_name == 'GetElementPtr':
            register_op = ir_pb2.RegisterOp()
            get_element_ptr = ir_pb2.GetElementPtr()

            if hasattr(op, 'type'):
                get_element_ptr.type.CopyFrom(convert_rtype(op.type))

            if hasattr(op, 'src'):
                get_element_ptr.src.CopyFrom(convert_value(op.src))

            if hasattr(op, 'src_type'):
                get_element_ptr.src_type.CopyFrom(convert_rtype(op.src_type))

            get_element_ptr.field = getattr(op, 'field', '')

            register_op.get_element_ptr.CopyFrom(get_element_ptr)
            op_proto.register_op.CopyFrom(register_op)

        elif op_class_name == 'SetElement':
            register_op = ir_pb2.RegisterOp()
            set_element = ir_pb2.SetElement()

            if hasattr(op, 'type'):
                set_element.type.CopyFrom(convert_rtype(op.type))

            if hasattr(op, 'src'):
                set_element.src.CopyFrom(convert_value(op.src))

            if hasattr(op, 'item'):
                set_element.item.CopyFrom(convert_value(op.item))

            set_element.field = getattr(op, 'field', '')

            register_op.set_element.CopyFrom(set_element)
            op_proto.register_op.CopyFrom(register_op)

        elif op_class_name == 'LoadAddress':
            register_op = ir_pb2.RegisterOp()
            load_address = ir_pb2.LoadAddress()

            if hasattr(op, 'type'):
                load_address.type.CopyFrom(convert_rtype(op.type))

            if hasattr(op, 'src'):
                if isinstance(op.src, str):
                    load_address.str_src = op.src
                elif isinstance(op.src, Register):
                    load_address.reg_src.CopyFrom(convert_register(op.src))
                elif hasattr(op.src, '__class__') and op.src.__class__.__name__ == 'LoadStatic':
                    load_static = ir_pb2.LoadStatic()
                    load_address.static_src.CopyFrom(load_static)

            register_op.load_address.CopyFrom(load_address)
            op_proto.register_op.CopyFrom(register_op)

        elif op_class_name == 'KeepAlive':
            register_op = ir_pb2.RegisterOp()
            keep_alive = ir_pb2.KeepAlive()

            if hasattr(op, 'src'):
                for src in op.src:
                    keep_alive.src.append(convert_value(src))

            keep_alive.steal = getattr(op, 'steal', False)

            register_op.keep_alive.CopyFrom(keep_alive)
            op_proto.register_op.CopyFrom(register_op)

        elif op_class_name == 'Unborrow':
            register_op = ir_pb2.RegisterOp()
            unborrow = ir_pb2.Unborrow()

            if hasattr(op, 'src'):
                unborrow.src.CopyFrom(convert_value(op.src))

            if hasattr(op, 'type'):
                unborrow.type.CopyFrom(convert_rtype(op.type))

            register_op.unborrow.CopyFrom(unborrow)
            op_proto.register_op.CopyFrom(register_op)

        else:
            logger.debug(f"Unknown operation type: {op_class_name}, using basic conversion")
            register_op = ir_pb2.RegisterOp()
            primitive_op = ir_pb2.PrimitiveOp()

            if hasattr(op, 'type'):
                primitive_op.type.CopyFrom(convert_rtype(op.type))

            register_op.primitive_op.CopyFrom(primitive_op)
            op_proto.register_op.CopyFrom(register_op)

    except Exception as e:
        logger.warning(f"Error converting operation {op.__class__.__name__}: {e}")
        logger.debug(traceback.format_exc())
        register_op = ir_pb2.RegisterOp()
        primitive_op = ir_pb2.PrimitiveOp()
        op_proto.register_op.CopyFrom(register_op)

    return op_proto


def convert_function(fn: FuncIR) -> ir_pb2.Function:
    decl = ir_pb2.FuncDecl(
        name=fn.decl.name if fn.decl else fn.name,
        module_name=fn.decl.module_name if fn.decl else "",
        sig=ir_pb2.FuncSignature()
    )

    blocks = []
    for block in fn.blocks:
        ops = []
        for op in block.ops:
            ops.append(convert_op(op))

        error_handler = None
        if block.error_handler:
            error_handler = ir_pb2.BasicBlock(label=block.error_handler.label)

        blocks.append(ir_pb2.BasicBlock(
            label=block.label,
            ops=ops,
            error_handler=error_handler,
            referenced=block.referenced
        ))

    return ir_pb2.Function(
        decl=decl,
        arg_regs=[],
        blocks=blocks,
        traceback_name=fn.traceback_name
    )


def convert_class(cls: MypyClassIR) -> ir_pb2.Class:
    attributes = {}
    for name, rtype in cls.attributes.items():
        attributes[name] = convert_rtype(rtype)

    methods = {}
    for name, method in cls.methods.items():
        methods[name] = convert_function(method)

    return ir_pb2.Class(
        name=cls.name,
        module_name=cls.module_name,
        is_trait=cls.is_trait,
        is_generated=cls.is_generated,
        is_abstract=cls.is_abstract,
        is_ext_class=cls.is_ext_class,
        is__class=True,
        is_augmented=cls.is_augmented,
        inherits_python=cls.inherits_python,
        has_dict=cls.has_dict,
        allow_interpreted_subclasses=cls.allow_interpreted_subclasses,
        needs_getseters=cls.needs_getseters,
        serializable=getattr(cls, '_serializable', False),
        builtin_base=cls.builtin_base or "",
        attributes=attributes,
        deletable=list(cls.deletable),
        methods=methods,
    )


def convert_cfg(fn: FuncIR, cfg) -> ir_pb2.FunctionCFG:
    logger.info(f"=== Converting CFG for function: {fn.name} ===")
    logger.info(f"Function object type: {type(fn)}")
    logger.info(f"Function name: {fn.name}")
    logger.info(f"Function decl name: {fn.decl.name if fn.decl else 'No decl'}")
    logger.info(f"Number of blocks: {len(fn.blocks)}")

    for i, block in enumerate(fn.blocks):
        logger.info(f"  Block {i}: label={block.label}, ops={len(block.ops)}")
        for j, op in enumerate(block.ops):
            logger.info(f"    Op {j}: type={type(op).__name__}, line={getattr(op, 'line', -1)}")

    cfg_proto = ir_pb2.CFG()

    exits = []
    for block in cfg.exits:
        logger.info(f"Exit block label: {block.label}")
        exits.append(ir_pb2.BasicBlock(label=block.label))
    cfg_proto.exits.extend(exits)

    blocks = []
    for block in fn.blocks:
        ops = []
        for op in block.ops:
            try:
                op_proto = convert_op(op)
                op_type = type(op).__name__
                logger.info(f"Converting op: {op_type} at line {getattr(op, 'line', -1)}")
                ops.append(op_proto)
            except Exception as e:
                logger.warning(f"Error converting op {op}: {e}")

        error_handler = None
        if block.error_handler:
            error_handler = ir_pb2.BasicBlock(
                label=block.error_handler.label,
                referenced=block.error_handler.referenced
            )

        blocks.append(ir_pb2.BasicBlock(
            label=block.label,
            ops=ops,
            error_handler=error_handler,
            referenced=block.referenced
        ))
        logger.info(f"Converted block with label: {block.label}")

    edges = []
    if hasattr(cfg, 'succ'):
        logger.info(f"CFG succ keys: {list(cfg.succ.keys())}")
        for src_block, dst_blocks in cfg.succ.items():
            if not src_block or not dst_blocks:
                continue

            src_label = src_block.label
            for dst_block in dst_blocks:
                if not dst_block:
                    continue

                dst_label = dst_block.label
                edges.append(ir_pb2.CFGEdge(
                    source=src_label,
                    target=dst_label,
                    type="normal"
                ))
                logger.info(f"Added edge: {src_label} -> {dst_label}")

    logger.info(f"Total edges: {len(edges)}")
    logger.info(f"Total blocks in proto: {len(blocks)}")

    return ir_pb2.FunctionCFG(
        function_name=fn.name,
        cfg=cfg_proto,
        blocks=blocks,
        edges=edges
    )


class IRService(ir_pb2_grpc.IRServiceServicer):

    def GetModules(self, request, context):
        logger.info("=== GetModules called ===")
        try:
            args = parse_source(request)
            modules = get_modules(**args)

            return ir_pb2.ModuleResponse(
                success=True,
                modules=[convert_module(m) for m in modules]
            )
        except Exception as e:
            logger.error(f"GetModules error: {e}")
            logger.error(traceback.format_exc())
            return ir_pb2.ModuleResponse(
                success=False,
                errors=[str(e)]
            )

    def GetClasses(self, request, context):
        logger.info("=== GetClasses called ===")
        try:
            args = parse_source(request)
            modules = get_modules(**args)

            classes = []
            for module in modules:
                classes.extend(module.classes.values())

            return ir_pb2.ClassResponse(
                success=True,
                classes=[convert_class(c) for c in classes]
            )
        except Exception as e:
            logger.error(f"GetClasses error: {e}")
            logger.error(traceback.format_exc())
            return ir_pb2.ClassResponse(
                success=False,
                errors=[str(e)]
            )

    def GetCFG(self, request, context):
        logger.info("=== GetCFG called ===")
        try:
            args = parse_source(request)

            if "files" in args:
                for file_path in args["files"]:
                    if not Path(file_path).exists():
                        error_msg = f"File not found: {file_path}"
                        logger.error(error_msg)
                        return ir_pb2.CFGResponse(
                            success=False,
                            errors=[error_msg]
                        )
                    else:
                        logger.info(f"File exists: {file_path}")

            modules = get_cfg(**args)
            logger.info(f"Got {len(modules)} modules for conversion")

            cfgs_proto = []

            for fn, cfg in modules:
                cfg_proto = convert_cfg(fn, cfg)
                cfgs_proto.append(cfg_proto)

                logger.info(f"Successfully converted CFG for {fn.name}")

            logger.info(f"Total CFGs generated: {len(cfgs_proto)}")

            cfgs_dict = []
            for cfg in cfgs_proto:
                try:
                    cfg_dict = MessageToDict(cfg, preserving_proto_field_name=True)
                    cfgs_dict.append(cfg_dict)
                except Exception as e:
                    logger.error(f"Error converting to dict: {e}")
                    cfgs_dict.append({"error": str(e)})

            with open('cfgs_debug.json', 'w', encoding='utf-8') as f:
                json.dump(cfgs_dict, f, indent=2, ensure_ascii=False, default=str)

            logger.info("Saved debug info to cfgs_debug.json")

            return ir_pb2.CFGResponse(
                success=bool(cfgs_proto),
                function_cfgs=cfgs_proto
            )

        except Exception as e:
            error_msg = str(e)
            logger.error(f"GetCFG error: {error_msg}")
            logger.error(traceback.format_exc())
            return ir_pb2.CFGResponse(
                success=False,
                errors=[error_msg]
            )

    def GetAll(self, request, context):
        logger.info("=== GetAll called ===")
        try:
            args = parse_source(request)
            modules = get_modules(**args)

            module_response = ir_pb2.ModuleResponse(
                success=True,
                modules=[convert_module(m) for m in modules]
            )

            classes = []
            for module in modules:
                classes.extend(module.classes.values())

            class_response = ir_pb2.ClassResponse(
                success=True,
                classes=[convert_class(c) for c in classes]
            )

            cfgs = []
            for module in modules:
                for fn in module.functions:
                    if fn.name == TOP_LEVEL_NAME:
                        continue
                    try:
                        exceptions.insert_exception_handling(fn)
                        cfg = dataflow.get_cfg(fn.blocks)
                        cfgs.append(convert_cfg(fn, cfg))
                    except Exception as e:
                        logger.debug(f"Failed to get CFG for {fn.name}: {e}")
                        continue

            cfg_response = ir_pb2.CFGResponse(
                success=bool(cfgs),
                function_cfgs=cfgs
            )

            return ir_pb2.CompleteResponse(
                success=True,
                modules=module_response,
                classes=class_response,
                cfgs=cfg_response
            )

        except Exception as e:
            error_msg = str(e)
            logger.error(f"GetAll error: {error_msg}")
            logger.error(traceback.format_exc())
            return ir_pb2.CompleteResponse(
                success=False,
                errors=[str(e)]
            )


def serve():
    logger.info("Starting gRPC server...")
    server = grpc.server(futures.ThreadPoolExecutor(max_workers=8))
    ir_pb2_grpc.add_IRServiceServicer_to_server(IRService(), server)
    server.add_insecure_port("[::]:50051")
    server.start()
    logger.info("IR gRPC server started on port 50051")
    server.wait_for_termination()


if __name__ == "__main__":
    serve()