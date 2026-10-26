from pathlib import Path
root=Path('../delivery/screenshots')
expected=['01-all-fixture-partial','02-stocks','03-viop','04-all','05-delayed-no-viop','06-chart-portrait-fixture','07-chart-summary-fixture','08-chart-landscape-fixture','09-chart-error-cleared','10-chart-financial-fixture','11-chart-financial-error-cleared','12-chart-sector-fixture','13-stocks-compact-fixture','14-stocks-filter-empty','15-stocks-no-data','16-stocks-landscape-fixture','17-viop-compact-underlying-fixture','18-viop-filter-empty','19-viop-contract-fixture','20-viop-landscape-fixture','21-stocks-touch-result','22-viop-touch-signals-unavailable','23-viop-touch-scan-busy','24-viop-touch-retry-complete','25-viop-analysis-no-connection','26-viop-analysis-all-contracts-fixture','27-viop-analysis-fetch-error-cleared','28-stocks-references-loaded-fixture','29-stocks-bist30-filter-fixture','30-stocks-reference-error-cleared','31-stocks-source-neon-dialog']
for name in expected:
    p=root/(name+'.png');b=p.read_bytes()
    assert b[:8]==b'\x89PNG\r\n\x1a\n' and len(b)>1000, f'Invalid screenshot: {name}'
    print(name, len(b), 'PNG verified')

