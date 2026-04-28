def main(n: int) -> int:
    acc = 1
    for i in range(1, n + 1):
        acc = acc * 2
        acc = acc - i
    return acc
