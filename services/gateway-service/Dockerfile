FROM krakend:2.10

COPY configuration/ /etc/krakend/config/

ENV FC_ENABLE=1 \
    FC_SETTINGS=/etc/krakend/config/settings \
    FC_TEMPLATES=/etc/krakend/config/templates \
    FC_PARTIALS=/etc/krakend/config/partials \
    FC_OUT=/tmp/krakend.json

# Render and validate at build time too, so a broken template fails the build
# instead of the container start. The config is rendered again on every start,
# which is what lets *_SERVICE_HOST / JWK_URL change without a rebuild. The
# rendered file is removed afterwards: it is written as root here, and the
# runtime user (krakend) could not overwrite it at start.
RUN krakend check -t -c /etc/krakend/config/krakend.tmpl && rm -f "$FC_OUT"

EXPOSE 7100

CMD ["run", "-c", "/etc/krakend/config/krakend.tmpl"]
