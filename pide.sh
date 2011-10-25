curl -s "http://localhost:3000/apps/WEB/primaria?cmd=current-pos&timeout=20000&uuid="$1 > /dev/null
date >> salida.log
