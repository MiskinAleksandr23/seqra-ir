def main(n: int) -> int:
    i = 0
    acc = 0
    while i < n:
        i = i + 1
        if i == 3:
            continue
        acc = acc + i
    return acc
