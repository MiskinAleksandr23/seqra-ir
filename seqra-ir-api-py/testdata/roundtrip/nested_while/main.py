def main(n: int) -> int:
    i = 0
    total = 0
    while i < n:
        j = 0
        while j < i:
            total = total + j
            j = j + 1
        i = i + 1
    return total
