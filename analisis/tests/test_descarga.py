"""De dónde sale la demo: descarga con tope de tamaño, descompresión y la carpeta de demos."""

import bz2
import gzip
from pathlib import Path

import httpx
import pytest
import zstandard

from app.config import Config
from app.descarga import DemoNoValida, DescargaFallida, demo_local, descargar, descomprimir, en_carpeta

DEMO = b"PBDEMS2\x00" + b"x" * 2000


def cfg(carpeta: Path, max_mb: int = 1) -> Config:
    return Config(carpeta=carpeta.resolve(), max_mb=max_mb, timeout_s=5)


def cliente(respuesta: httpx.Response) -> httpx.Client:
    return httpx.Client(transport=httpx.MockTransport(lambda _: respuesta))


def test_descarga(tmp_path: Path):
    destino = tmp_path / "d"
    descargar("https://demos/x.dem", destino, cfg(tmp_path), cliente(httpx.Response(200, content=DEMO)))
    assert destino.read_bytes() == DEMO


def test_descarga_caducada_o_sin_permiso_no_se_repite(tmp_path: Path):
    for codigo in (403, 404, 410):
        with pytest.raises(DemoNoValida):
            descargar("https://demos/x.dem", tmp_path / "d", cfg(tmp_path), cliente(httpx.Response(codigo)))
    with pytest.raises(DescargaFallida):
        descargar("https://demos/x.dem", tmp_path / "d", cfg(tmp_path), cliente(httpx.Response(502)))


def test_descarga_con_tope_de_tamano(tmp_path: Path):
    grande = b"x" * (1024 * 1024 + 1)
    with pytest.raises(DemoNoValida, match="1 MB"):
        descargar("https://demos/x.dem", tmp_path / "d", cfg(tmp_path), cliente(httpx.Response(200, content=grande)))


def test_descomprime_por_los_primeros_bytes(tmp_path: Path):
    for nombre, datos in (
        ("a.gz", gzip.compress(DEMO)),
        ("a.zst", zstandard.ZstdCompressor().compress(DEMO)),
        ("a.bz2", bz2.compress(DEMO)),
    ):
        origen = tmp_path / nombre
        origen.write_bytes(datos)
        assert descomprimir(origen, tmp_path / f"{nombre}.dem", cfg(tmp_path)).read_bytes() == DEMO
    sin_comprimir = tmp_path / "b.dem"
    sin_comprimir.write_bytes(DEMO)
    assert descomprimir(sin_comprimir, tmp_path / "c.dem", cfg(tmp_path)) == sin_comprimir


def test_comprimida_rota(tmp_path: Path):
    origen = tmp_path / "rota.gz"
    origen.write_bytes(gzip.compress(DEMO)[:20])
    with pytest.raises(DemoNoValida):
        descomprimir(origen, tmp_path / "x.dem", cfg(tmp_path))


def test_solo_ficheros_de_la_carpeta(tmp_path: Path):
    carpeta = tmp_path / "demos"
    carpeta.mkdir()
    (carpeta / "1-abc.dem").write_bytes(DEMO)
    (tmp_path / "fuera.dem").write_bytes(DEMO)
    assert en_carpeta(cfg(carpeta), "1-abc.dem").name == "1-abc.dem"
    for nombre in ("../fuera.dem", "no-existe.dem", str(tmp_path / "fuera.dem")):
        with pytest.raises(DemoNoValida):
            en_carpeta(cfg(carpeta), nombre)


def test_lo_temporal_se_borra_al_acabar(tmp_path: Path):
    (tmp_path / "1-abc.dem.gz").write_bytes(gzip.compress(DEMO))
    with demo_local(cfg(tmp_path), archivo="1-abc.dem.gz") as ruta:
        assert ruta.read_bytes() == DEMO
        temporal = ruta.parent
    assert not temporal.exists()
    assert (tmp_path / "1-abc.dem.gz").exists()  # la de la carpeta no se toca
