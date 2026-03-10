# Nova Grafana Checklist

## Summary

Preflight and verification checklist for every Grafana dashboard provisioning edit: confirm target files and UID, safe edit sequence (stop/edit/validate/start), container-truth checks, provisioning log checks, and UI checks.

## Key points

- Preflight: confirm target dashboard JSON, provider YAML, and dashboard `uid`/title.
- Safe sequence: stop Grafana → edit → validate JSON → start Grafana.
- Container-truth: verify file and content inside container, not only on host.
- Provisioning logs: grep for provision/dashboard/failed/error after startup.
- UI: verify UID, tiles, Datavine row and table nesting, column headers.

## Source (markdown)

<details class="skill-source-wrap"><summary>Click to expand</summary>

```markdown
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
```

</details>
