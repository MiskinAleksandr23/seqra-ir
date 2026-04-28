from helper import score


def main(limit: int) -> int:
    row = 0
    found = -1
    while row < limit:
        col = 0
        while col < limit:
            current = score(row, col)
            if current > 0:
                if current % 3 == 0:
                    found = current
                    break
            col = col + 1
        if found != -1:
            break
        row = row + 1
    return found
