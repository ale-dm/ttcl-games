"""Cliente de la API Java para las consultas del chat (P9). Solo lee, y guarda cada respuesta un minuto para no repetir
la misma consulta (Gemini a veces pide lo mismo dos veces, y la misma pregunta se puede repetir).
"""

import threading
import time
from collections import OrderedDict
from urllib.parse import urlencode

import httpx

from .config import get_config

CADUCIDAD = 60  # segundos
MAX_CACHE = 200


class ApiError(Exception):
    """La API no responde o dice que la consulta no vale. El mensaje se le pasa a Gemini tal cual."""


class _Cache:
    def __init__(self) -> None:
        self._datos: OrderedDict[str, tuple[float, object]] = OrderedDict()
        self._lock = threading.Lock()

    def get(self, clave: str) -> object | None:
        with self._lock:
            guardado = self._datos.get(clave)
            if guardado is None or guardado[0] < time.monotonic():
                return None
            return guardado[1]

    def set(self, clave: str, valor: object) -> None:
        with self._lock:
            self._datos[clave] = (time.monotonic() + CADUCIDAD, valor)
            self._datos.move_to_end(clave)
            while len(self._datos) > MAX_CACHE:
                self._datos.popitem(last=False)

    def limpiar(self) -> None:
        with self._lock:
            self._datos.clear()


cache = _Cache()
_cliente: httpx.Client | None = None


def _get_cliente() -> httpx.Client:
    global _cliente
    if _cliente is None:
        cfg = get_config()
        _cliente = httpx.Client(base_url=cfg.api_url, timeout=cfg.api_timeout_ms / 1000)
    return _cliente


def consultar(ruta: str, params: dict[str, object]) -> object:
    """GET a la API con los parámetros que no sean None. Lanza ApiError si no responde o responde con error."""
    params = {k: v for k, v in params.items() if v is not None}
    clave = f"{ruta}?{urlencode(sorted(params.items()))}"
    if (guardado := cache.get(clave)) is not None:
        return guardado
    try:
        respuesta = _get_cliente().get(ruta, params=params)
    except httpx.HTTPError as err:
        raise ApiError(f"la API no responde ({type(err).__name__})") from err
    if respuesta.status_code >= 400:
        try:
            mensaje = respuesta.json().get("error")
        except ValueError:
            mensaje = None
        raise ApiError(mensaje or f"la API respondió {respuesta.status_code}")
    datos = respuesta.json()
    cache.set(clave, datos)
    return datos
