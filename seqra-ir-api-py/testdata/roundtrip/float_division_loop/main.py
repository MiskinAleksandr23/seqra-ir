def main(n: int) -> float:
    i = 1
    acc = 0.0
    while i <= n:
        acc = acc + (i / 2)
        i = i + 1
    return acc
