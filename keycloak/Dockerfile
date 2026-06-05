FROM quay.io/keycloak/keycloak:26.6.3
COPY realm-export.json /opt/keycloak/data/import/realm-export.json
EXPOSE 8080 9000
ENTRYPOINT ["/opt/keycloak/bin/kc.sh"]
