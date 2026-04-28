from math_ops import scale, shift


def transform(x: int) -> int:
    if x > 10:
        return scale(x) - 4
    if x == 10:
        return shift(x)
    return scale(x) + 1
