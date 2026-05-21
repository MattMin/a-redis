FROM alpine:3.20

RUN apk add --no-cache openssh shadow \
    && ssh-keygen -A \
    && mkdir -p /var/run/sshd /authorized_keys

COPY sshd_config /etc/ssh/sshd_config
COPY entrypoint.sh /entrypoint.sh

RUN chmod +x /entrypoint.sh

EXPOSE 22

ENTRYPOINT ["/entrypoint.sh"]

