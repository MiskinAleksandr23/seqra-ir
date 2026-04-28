def weight(n: int) -> int:
    if n <= 1:
        return 1
    return weight(n - 1) + 1


def main(limit: int) -> int:
    i = 0
    acc = 0
    while i < limit:
        acc = acc + weight(i)
        i = i + 1
    return acc
