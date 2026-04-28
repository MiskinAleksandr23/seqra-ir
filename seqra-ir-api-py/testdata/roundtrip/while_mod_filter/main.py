def main(n: int) -> int:
    i = 0
    acc = 0
    while i < n:
        if i % 2 == 0:
            i = i + 1
            continue
        acc = acc + i
        i = i + 1
    return acc
