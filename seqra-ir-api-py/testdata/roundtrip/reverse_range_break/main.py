def main(n: int) -> int:
    acc = 0
    for i in range(n, -1, -1):
        if i == 4:
            continue
        acc = acc + i
        if acc > 12:
            break
    return acc
