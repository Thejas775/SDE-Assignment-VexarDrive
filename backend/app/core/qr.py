import io
from uuid import UUID

import qrcode
import qrcode.image.svg

VEHICLE_URI_SCHEME = "fleet://vehicle/"


def vehicle_payload(vehicle_id: UUID) -> str:
    """Deep link so a scan opens the vehicle directly in the mobile app."""
    return f"{VEHICLE_URI_SCHEME}{vehicle_id}"


def parse_vehicle_code(code: str) -> UUID:
    """Accept either the deep link or a bare UUID pasted by hand."""
    raw = code.strip()
    if raw.startswith(VEHICLE_URI_SCHEME):
        raw = raw[len(VEHICLE_URI_SCHEME) :]
    return UUID(raw)


def render(payload: str, image_format: str = "png", box_size: int = 10) -> tuple[bytes, str]:
    code = qrcode.QRCode(
        version=None,
        error_correction=qrcode.constants.ERROR_CORRECT_M,
        box_size=box_size,
        border=2,
    )
    code.add_data(payload)
    code.make(fit=True)

    buffer = io.BytesIO()
    if image_format == "svg":
        code.make_image(image_factory=qrcode.image.svg.SvgPathImage).save(buffer)
        return buffer.getvalue(), "image/svg+xml"
    code.make_image(fill_color="black", back_color="white").save(buffer, format="PNG")
    return buffer.getvalue(), "image/png"
