def main(n: int) -> int:
    acc = 0
    for i in range(1, n + 1):
        acc = acc + (i // 2)
    return acc
