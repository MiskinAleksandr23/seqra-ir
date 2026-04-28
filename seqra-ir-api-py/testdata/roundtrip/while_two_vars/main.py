def main(n: int) -> int:
    i = 0
    a = 1
    b = 0
    while i < n:
        b = b + a
        a = a + 1
        i = i + 1
    return b
