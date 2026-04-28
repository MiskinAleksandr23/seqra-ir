def main(n: int) -> int:
    acc = 0
    for i in range(n):
        if i == 2:
            continue
        if i == 5:
            break
        acc = acc + i
    return acc
