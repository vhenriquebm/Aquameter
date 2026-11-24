#!/usr/bin/env python3
"""
Simula um gateway IoT de campo (ex.: ESP32 lendo hidrômetros com saída de pulso)
enviando leituras para a API do AquaMeter. Usa só a biblioteca padrão do Python.

Uso:
    python3 sensor_simulado.py --medidor 1 --valor 3.2
    python3 sensor_simulado.py --medidor 2 --valor 2.4 --data-hora 2026-11-24T02:00:00   # leitura de madrugada
"""
import argparse
import json
import urllib.request

parser = argparse.ArgumentParser(description="Envia uma leitura para o AquaMeter")
parser.add_argument("--api", default="http://localhost:8080", help="URL base da API")
parser.add_argument("--medidor", type=int, required=True, help="ID do medidor")
parser.add_argument("--valor", type=float, required=True, help="Consumo no intervalo (m³ ou kWh)")
parser.add_argument("--data-hora", help="ISO 8601, ex.: 2026-11-24T02:00:00 (padrão: agora)")
args = parser.parse_args()

payload = {"medidorId": args.medidor, "valor": args.valor}
if args.data_hora:
    payload["dataHora"] = args.data_hora

req = urllib.request.Request(
    f"{args.api}/api/leituras",
    data=json.dumps(payload).encode(),
    headers={"Content-Type": "application/json"},
    method="POST",
)
with urllib.request.urlopen(req) as resp:
    corpo = json.loads(resp.read() or b"{}")
    print(f"HTTP {resp.status} - leitura registrada")
    for alerta in corpo.get("alertasGerados", []):
        print("ALERTA:", alerta)
