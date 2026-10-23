#!/bin/sh
cd "$(dirname "$0")"
exec java "$@" -cp out mailclient.MailClient
