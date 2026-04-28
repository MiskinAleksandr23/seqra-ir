def main(n: int) -> int:
    total = 0
    for i in range(n):
        if i == 3:
            total = total + 30
            break
        total = total + i
    else:
        total = total - 7
    return total
