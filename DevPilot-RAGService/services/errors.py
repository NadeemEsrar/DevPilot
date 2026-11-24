class ServiceError(Exception):
    """A failure with a message that is safe to show the user."""

    def __init__(self, status: int, message: str):
        super().__init__(message)
        self.status = status
        self.message = message
