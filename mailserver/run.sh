#!/bin/sh
# Chay server. Co the them tham so: ./run.sh -Dsmtp.port=25 -Dpop3.port=110 -Ddb.path=data/mail.db
cd "$(dirname "$0")"
exec java "$@" -cp "out:lib/*" mailserver.MailServer
