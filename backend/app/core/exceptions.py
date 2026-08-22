class CustomException(Exception):
    def __init__(self, message: str = "An unexpected error occurred", code: int = 500):
        self.code = code
        self.message = message
        super().__init__(self.message)


class NotFoundError(CustomException):
    def __init__(self, message: str = "Resource not found"):
        super().__init__(message, 404)


class ConflictError(CustomException):
    def __init__(self, message: str = "Conflict with existing state"):
        super().__init__(message, 409)


class ValidationError(CustomException):
    def __init__(self, message: str = "Invalid request"):
        super().__init__(message, 422)


class UnauthorizedError(CustomException):
    def __init__(self, message: str = "Not authenticated"):
        super().__init__(message, 401)


class ForbiddenError(CustomException):
    def __init__(self, message: str = "Not permitted"):
        super().__init__(message, 403)
