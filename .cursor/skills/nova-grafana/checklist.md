# Nova Grafana Checklist

Use this checklist for every Grafana dashboard provisioning edit.

## A) Preflight

- [ ] Confirm target file: `/home/perfect_edge13/Docker/grafana/provisioning/dashboards/json/status.json`
- [ ] Confirm provider file: `/home/perfect_edge13/Docker/grafana/provisioning/dashboards/default.yml`
- [ ] Confirm target dashboard `uid` and title before edits

## B) Safe edit sequence

- [ ] Stop Grafana:
  - `cd /home/perfect_edge13/Docker/grafana`
  - `sudo docker compose stop grafana`
- [ ] Edit dashboard JSON deterministically (structured JSON rewrite preferred)
- [ ] Validate syntax:
  - `python3 -m json.tool "/home/perfect_edge13/Docker/grafana/provisioning/dashboards/json/status.json"`
- [ ] Start Grafana:
  - `sudo docker compose up -d`

## C) Container-truth checks

- [ ] Verify file exists in container:
  - `sudo docker compose exec grafana sh -lc 'ls -l /etc/grafana/provisioning/dashboards/json/status.json'`
- [ ] Verify container file content:
  - `sudo docker compose exec grafana sh -lc 'sed -n "1,260p" /etc/grafana/provisioning/dashboards/json/status.json'`

## D) Provisioning log checks

- [ ] Check logs for provisioning/parser errors:
  - `sudo docker compose logs grafana 2>&1 | grep -i -E "provision|dashboard|failed|error"`
- [ ] If error includes `invalid character ':' after array element`, stop and re-validate JSON before retrying

## E) UI checks

- [ ] Correct dashboard UID loaded
- [ ] Top tiles present and consistent size
- [ ] Datavine status tile present when required
- [ ] Datavine row collapsed by default
- [ ] Datavine table nested under Datavine row (not top-level sibling)
- [ ] Datavine column headers mapped (not `Value #A/#B/...`)
