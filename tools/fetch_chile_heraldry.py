#!/usr/bin/env python3
"""
fetch_chile_heraldry.py
Extractor de heráldica y vexilología oficial de Chile (País > Regiones > Provincias > Comunas)
desde Wikidata SPARQL y Wikimedia Commons para Corta App.

Cumple con:
1. Extracción en tiempo de compilación (Build-Time / Offline Invariant < 2ms).
2. Jerarquía oficial DPA: Regiones (16) y Comunas (346).
3. Soporte para miniaturas optimizadas de Wikimedia (?width=200).
4. Generación de catálogo estructurado JSON y descarga de recursos.
"""

import argparse
import json
import os
import sys
import time
import urllib.parse
import urllib.request
from typing import Any, Dict, List, Optional

WIKIDATA_SPARQL_URL = "https://query.wikidata.org/sparql"
USER_AGENT = "CortaApp/1.0 (contacto@corta.app; https://github.com/4xiom4/corta)"

SPARQL_QUERY = """
SELECT ?comuna ?comunaLabel ?provinciaLabel ?regionLabel ?banderaComuna ?escudoComuna ?banderaRegion ?escudoRegion WHERE {
  ?comuna wdt:P31 wd:Q1840161 ;
          wdt:P131 ?provincia .
  ?provincia wdt:P31 wd:Q1153408 ;
             wdt:P131 ?region .
  ?region wdt:P31 wd:Q590080 .
  OPTIONAL { ?comuna wdt:P41 ?banderaComuna . }
  OPTIONAL { ?comuna wdt:P94 ?escudoComuna . }
  OPTIONAL { ?region wdt:P41 ?banderaRegion . }
  OPTIONAL { ?region wdt:P94 ?escudoRegion . }
  SERVICE wikibase:label { bd:serviceParam wikibase:language "es". }
}
ORDER BY ?regionLabel ?provinciaLabel ?comunaLabel
"""

def query_wikidata() -> List[Dict[str, Any]]:
    """Consulta el endpoint de Wikidata SPARQL con reintentos."""
    params = {
        "format": "json",
        "query": SPARQL_QUERY
    }
    url = f"{WIKIDATA_SPARQL_URL}?{urllib.parse.urlencode(params)}"
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})

    for attempt in range(1, 4):
        try:
            print(f"[SPARQL] Consultando Wikidata Query Service (Intento {attempt}/3)...")
            with urllib.request.urlopen(req, timeout=45) as response:
                payload = json.loads(response.read().decode("utf-8"))
                bindings = payload.get("results", {}).get("bindings", [])
                print(f"[SPARQL] Registros obtenidos con éxito: {len(bindings)}")
                return bindings
        except Exception as e:
            print(f"[WARN] Error en intento {attempt}: {e}")
            if attempt < 3:
                time.sleep(3)
            else:
                raise

def process_bindings(bindings: List[Dict[str, Any]]) -> Dict[str, Any]:
    """Procesa los resultados de SPARQL y los agrupa en una estructura jerárquica."""
    regions_map: Dict[str, Dict[str, Any]] = {}
    comunas_list: List[Dict[str, Any]] = []

    for item in bindings:
        comuna_qid = item.get("comuna", {}).get("value", "").split("/")[-1]
        comuna_name = item.get("comunaLabel", {}).get("value", "")
        provincia_name = item.get("provinciaLabel", {}).get("value", "")
        region_name = item.get("regionLabel", {}).get("value", "")
        
        escudo_comuna = item.get("escudoComuna", {}).get("value")
        bandera_comuna = item.get("banderaComuna", {}).get("value")
        escudo_region = item.get("escudoRegion", {}).get("value")
        bandera_region = item.get("banderaRegion", {}).get("value")

        if region_name and region_name not in regions_map:
            regions_map[region_name] = {
                "name": region_name,
                "escudo_url": escudo_region,
                "bandera_url": bandera_region,
                "provincias": set()
            }
        if region_name and provincia_name:
            regions_map[region_name]["provincias"].add(provincia_name)

        comunas_list.append({
            "qid": comuna_qid,
            "name": comuna_name,
            "provincia": provincia_name,
            "region": region_name,
            "escudo_url": escudo_comuna,
            "bandera_url": bandera_comuna
        })

    # Convert sets to sorted lists for JSON serialization
    serialized_regions = []
    for r_name in sorted(regions_map.keys()):
        r_data = regions_map[r_name]
        serialized_regions.append({
            "name": r_name,
            "escudo_url": r_data["escudo_url"],
            "bandera_url": r_data["bandera_url"],
            "provincias": sorted(list(r_data["provincias"]))
        })

    return {
        "metadata": {
            "source": "Wikidata / Wikimedia Commons",
            "country": "Chile",
            "total_comunas": len(comunas_list),
            "total_regiones": len(serialized_regions),
            "generated_at": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())
        },
        "regiones": serialized_regions,
        "comunas": comunas_list
    }

def download_thumbnail(url: str, output_path: str, width: int = 200) -> bool:
    """Descarga la miniatura PNG de 200px generada por Wikimedia Commons."""
    if not url:
        return False
    # Wikimedia dynamic thumb parameter
    thumb_url = f"{url}?width={width}" if not url.endswith(f"?width={width}") else url
    req = urllib.request.Request(thumb_url, headers={"User-Agent": USER_AGENT})

    try:
        with urllib.request.urlopen(req, timeout=15) as resp:
            data = resp.read()
            os.makedirs(os.path.dirname(output_path), exist_ok=True)
            with open(output_path, "wb") as f:
                f.write(data)
            return True
    except Exception as e:
        print(f"[ERR] Fallo al descargar {thumb_url}: {e}")
        return False

def main():
    parser = argparse.ArgumentParser(description="Extractor de Heráldica de Chile para Corta App")
    parser.add_argument("--json-out", default="tools/chile_heraldry_catalog.json", help="Ruta de guardado del JSON")
    parser.add_argument("--download-images", action="store_true", help="Descargar miniaturas PNG (200px)")
    parser.add_argument("--images-dir", default="tools/heraldry_images", help="Directorio destino de imágenes")
    parser.add_argument("--limit-downloads", type=int, default=10, help="Límite de imágenes para prueba")
    args = parser.parse_args()

    print("=== CORTA APP · EXTRACTOR DE HERÁLDICA CHILENA ===")
    bindings = query_wikidata()
    catalog = process_bindings(bindings)

    with open(args.json_out, "w", encoding="utf-8") as f:
        json.dump(catalog, f, ensure_ascii=False, indent=2)
    print(f"[OK] Catálogo exportado a: {args.json_out}")
    print(f"     Regiones: {catalog['metadata']['total_regiones']}, Comunas: {catalog['metadata']['total_comunas']}")

    if args.download_images:
        print(f"[INFO] Descargando hasta {args.limit_downloads} miniaturas en: {args.images_dir}")
        count = 0
        for comuna in catalog["comunas"]:
            if count >= args.limit_downloads:
                break
            escudo = comuna.get("escudo_url")
            if escudo:
                clean_name = comuna["name"].lower().replace(" ", "_").replace("á", "a").replace("é", "e").replace("í", "i").replace("ó", "o").replace("ú", "u")
                dest = os.path.join(args.images_dir, f"escudo_{clean_name}.png")
                print(f" -> Descargando escudo de {comuna['name']}...")
                if download_thumbnail(escudo, dest, width=200):
                    count += 1
                time.sleep(0.5) # Respetar rate limits de Wikimedia
        print(f"[OK] {count} imágenes descargadas correctamente.")

if __name__ == "__main__":
    main()
