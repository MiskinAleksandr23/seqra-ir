def main(n: int) -> int:
    i = 0
    acc = 0
    while i < n:
        acc = acc + i
        i = i + 1
    else:
        acc = acc + 100
    return acc
