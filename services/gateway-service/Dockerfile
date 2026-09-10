FROM devopsfaith/krakend:2.10

COPY config/ /etc/krakend/config/

ENV FC_ENABLE=1 \
    FC_SETTINGS=/etc/krakend/config/settings \
    FC_TEMPLATES=/etc/krakend/config/templates \
    FC_OUT=/tmp/krakend.json

EXPOSE 8080

CMD ["run", "-c", "/etc/krakend/config/krakend.tmpl"]