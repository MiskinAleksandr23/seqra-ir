def helper(n: int) -> int:
    if n <= 0:
        return 0
    return n + helper(n - 1)


def main(n: int) -> int:
    return helper(n)
