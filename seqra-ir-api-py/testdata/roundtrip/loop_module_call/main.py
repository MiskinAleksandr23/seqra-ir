from helper import step


def main(n: int) -> int:
    i = 0
    acc = 0
    while i < n:
        acc = step(acc, i)
        i = i + 1
    return acc
