def main(n: int) -> int:
    acc = 0
    for i in range(0, n, 2):
        acc = acc + i
    return acc
