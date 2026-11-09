"""Python twin of DesignSimulator.kt, used to tune scenarios."""
import itertools

COST = dict(cdn=40, lb=30, app=50, cache=60, queue=40, shard=100, replica=70)
RANGE = dict(cdn=(0, 1), lb=(0, 1), app=(1, 20), cache=(0, 1), queue=(0, 1), shard=(1, 8), replica=(0, 6))
APP_CAP, CACHE_HIT, SHARD_READ, SHARD_WRITE, REPLICA_READ = 1000, 0.85, 2000, 1000, 2000


def f(u):
    u = min(u, 1.0)
    return 1 + 3 * u ** 4


def run(sc, a):
    peak = sc['rps'] * sc.get('peak', 1.0)
    edge = peak * sc.get('static', 0.0) if a['cdn'] else 0.0
    app_load = peak - edge
    reachable = a['app'] if (a['lb'] or a['app'] == 1) else 1
    app_cap = reachable * APP_CAP
    reads = app_load * sc['read']
    writes = app_load * (1 - sc['read'])
    db_reads = reads * (1 - CACHE_HIT) if a['cache'] else reads
    db_writes = writes / sc.get('peak', 1.0) if a['queue'] else writes
    read_cap = SHARD_READ * a['shard'] + REPLICA_READ * a['replica']
    write_cap = SHARD_WRITE * a['shard']
    ua, ur, uw = app_load / app_cap, db_reads / read_cap, db_writes / write_cap
    lat = (2 if a['lb'] else 0) + 10 * f(ua)
    rl = (2 + (1 - CACHE_HIT) * 15 * f(ur)) if a["cache"] else 15 * f(ur)
    wl = 4 if a['queue'] else 25 * f(uw)
    lat += sc['read'] * rl + (1 - sc['read']) * wl
    cost = sum(COST[k] * v for k, v in a.items())
    overloaded = max(ua, ur, uw) > 1.0
    passed = (not overloaded) and cost <= sc['budget'] and lat <= sc['lat']
    stars = 0
    if passed:
        stars = 1 + (cost <= 0.9 * sc['budget']) + (max(ua, ur, uw) <= 0.8)
    return dict(passed=passed, stars=stars, lat=lat, cost=cost, u=(ua, ur, uw))


def search(sc):
    keys = list(RANGE)
    sols = []
    for vals in itertools.product(*[range(lo, hi + 1) for lo, hi in RANGE.values()]):
        a = dict(zip(keys, vals))
        r = run(sc, a)
        if r['passed']:
            sols.append((r['cost'], r['stars'], a, r))
    sols.sort(key=lambda s: s[0])
    return sols


def report(name, sc):
    base = dict(cdn=0, lb=0, app=1, cache=0, queue=0, shard=1, replica=0)
    sols = search(sc)
    print(f"== {name}: {len(sols)} passing configs; start config passes={run(sc, base)['passed']}")
    three = [s for s in sols if s[1] == 3]
    for s in sols[:3]:
        print("  cheapest", s[0], s[1], {k: v for k, v in s[2].items() if v}, f"lat={s[3]['lat']:.0f}")
    print(f"  3-star count={len(three)}", (three[0][0], {k: v for k, v in three[0][2].items() if v}) if three else None)
    return sols


if __name__ == '__main__':
    import json, sys
    for path in sys.argv[1:]:
        data = json.load(open(path))
        for lv in data['levels']:
            for c in lv['challenges']:
                if c['type'] == 'design':
                    s = c['scenario']
                    sc = dict(rps=s['baseRps'], peak=s.get('peakMultiplier', 1.0), read=s['readRatio'],
                              static=s.get('staticRatio', 0.0), budget=s['budget'], lat=s['maxLatencyMs'])
                    report(c['id'], sc)
