import hashlib


def hash_flag(raw_flag: str) -> str:
    """
    SHA-256 of the raw flag string -- this is what gets sent to the backend
    during job completion. The raw flag itself is written to the VM but
    never persisted in the database.
    """
    return hashlib.sha256(raw_flag.encode('utf-8')).hexdigest()
