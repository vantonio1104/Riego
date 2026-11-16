#!/usr/bin/env bash
# Genera la CA propia y el certificado del broker, y copia ca.crt a la app.
# Ejecutar en Git Bash desde la carpeta broker/:  bash generar_certificados.sh
# Despues de regenerar: recompilar la app (lleva ca.crt adentro) y reiniciar Mosquitto.
set -e
export MSYS_NO_PATHCONV=1 # evita que Git Bash convierta "/CN=..." en una ruta de Windows
cd "$(dirname "$0")"
mkdir -p certs
cd certs
openssl req -x509 -newkey rsa:2048 -nodes -days 825 \
  -keyout ca.key -out ca.crt -subj "/CN=Riego IoT CA"
openssl req -newkey rsa:2048 -nodes \
  -keyout server.key -out server.csr -subj "/CN=broker-riego"
printf "basicConstraints=CA:FALSE\nkeyUsage=digitalSignature,keyEncipherment\nextendedKeyUsage=serverAuth\n" > server.ext
openssl x509 -req -in server.csr -CA ca.crt -CAkey ca.key -CAcreateserial \
  -days 825 -out server.crt -extfile server.ext
rm -f server.csr server.ext ca.srl
openssl verify -CAfile ca.crt server.crt
mkdir -p ../../app/src/main/res/raw
cp ca.crt ../../app/src/main/res/raw/ca.crt
echo "Listo: certs/ (broker) y app/src/main/res/raw/ca.crt (app)"
