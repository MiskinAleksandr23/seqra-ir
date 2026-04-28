def dfs(node: int, target: int, limit: int) -> int:
    if node > limit:
        return -1
    if node == target:
        return node

    left = dfs(node * 2, target, limit)
    if left != -1:
        return left

    return dfs(node * 2 + 1, target, limit)


def main(target: int) -> int:
    return dfs(1, target, 20)
