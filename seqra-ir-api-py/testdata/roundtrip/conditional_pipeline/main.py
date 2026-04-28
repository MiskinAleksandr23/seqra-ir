from helper import transform
from math_ops import shift


def main(y: int) -> int:
    value = transform(y)
    if value >= 20:
        return value - 5
    if value != 13:
        return shift(value)
    return value + 2
