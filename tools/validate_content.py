#!/usr/bin/env python3
"""Validates AlgoArch content packs.

- unique ids, valid answer indices, sane order challenges
- re-runs every trace challenge's algorithm and checks the stored answer
- brute-forces every design scenario with the simulator twin to make sure it is
  solvable and that the starting architecture fails

Usage: python3 tools/validate_content.py
"""
import glob
import heapq
import json
import os
import sys
from collections import defaultdict, deque

sys.path.insert(0, os.path.dirname(__file__))
import sim  # noqa: E402

ROOT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets", "content")
errors = []


def err(msg):
    errors.append(msg)


def adjacency(c, directed):
    adj = defaultdict(list)
    for e in c["edges"]:
        adj[e["from"]].append((e["to"], e.get("weight", 1)))
        if not directed:
            adj[e["to"]].append((e["from"], e.get("weight", 1)))
    return adj


def tree_children(c):
    """Children ordered left-to-right by x position."""
    pos = {n["id"]: n["x"] for n in c["nodes"]}
    kids = defaultdict(list)
    for e in c["edges"]:
        kids[e["from"]].append(e["to"])
    for k in kids:
        kids[k].sort(key=lambda n: pos[n])
    return kids, pos


def run_trace(c):
    algo = c["algo"]
    start = c.get("start")
    if algo in ("bfs", "dfs", "dijkstra"):
        adj = adjacency(c, c.get("directed", False))
        for k in adj:
            adj[k].sort()
        if algo == "bfs":
            seen, order, q = {start}, [], deque([start])
            while q:
                n = q.popleft()
                order.append(n)
                for m, _ in adj[n]:
                    if m not in seen:
                        seen.add(m)
                        q.append(m)
            return order
        if algo == "dfs":
            seen, order = set(), []

            def go(n):
                seen.add(n)
                order.append(n)
                for m, _ in adj[n]:
                    if m not in seen:
                        go(m)
            go(start)
            return order
        dist, order, pq, done = {start: 0}, [], [(0, start)], set()
        while pq:
            d, n = heapq.heappop(pq)
            if n in done:
                continue
            done.add(n)
            order.append(n)
            for m, w in adj[n]:
                if d + w < dist.get(m, 1e18):
                    dist[m] = d + w
                    heapq.heappush(pq, (d + w, m))
        ties = defaultdict(list)
        for n, d in dist.items():
            ties[d].append(n)
        if any(len(v) > 1 for v in ties.values()):
            err(f"{c['id']}: dijkstra has tied distances {dict(ties)}")
        return order
    if algo == "topo":
        indeg = {n["id"]: 0 for n in c["nodes"]}
        adj = defaultdict(list)
        for e in c["edges"]:
            adj[e["from"]].append(e["to"])
            indeg[e["to"]] += 1
        ready = sorted(n for n, d in indeg.items() if d == 0)
        order = []
        while ready:
            n = ready.pop(0)
            order.append(n)
            for m in adj[n]:
                indeg[m] -= 1
                if indeg[m] == 0:
                    ready.append(m)
                    ready.sort()
        return order
    kids, pos = tree_children(c)
    order = []

    def walk(n):
        ch = kids.get(n, [])
        left = [k for k in ch if pos[k] < pos[n]]
        right = [k for k in ch if pos[k] >= pos[n]]
        if algo == "preorder":
            order.append(n)
        for k in left:
            walk(k)
        if algo == "inorder":
            order.append(n)
        for k in right:
            walk(k)
        if algo == "postorder":
            order.append(n)
    walk(start)
    return order


def main():
    ids = set()
    answer_slots = defaultdict(int)
    files = sorted(glob.glob(os.path.join(ROOT, "*.json")))
    n_challenges = 0
    for path in files:
        region = json.load(open(path))
        for level in region["levels"]:
            if level["id"] in ids:
                err(f"duplicate level id {level['id']}")
            ids.add(level["id"])
            for c in level["challenges"]:
                n_challenges += 1
                cid = c["id"]
                if cid in ids:
                    err(f"duplicate id {cid}")
                ids.add(cid)
                t = c["type"]
                if t == "choice":
                    if not 0 <= c["answer"] < len(c["options"]):
                        err(f"{cid}: answer index out of range")
                    if len(set(c["options"])) != len(c["options"]):
                        err(f"{cid}: duplicate options")
                    answer_slots[c["answer"]] += 1
                elif t == "order":
                    pool = c["lines"] + c.get("distractors", [])
                    if len(set(pool)) != len(pool):
                        err(f"{cid}: duplicate lines")
                elif t == "trace":
                    node_ids = {n["id"] for n in c["nodes"]}
                    for e in c["edges"]:
                        if e["from"] not in node_ids or e["to"] not in node_ids:
                            err(f"{cid}: edge references unknown node")
                    got = run_trace(c)
                    if got != c["answer"]:
                        err(f"{cid}: stored answer {c['answer']} but {c['algo']} gives {got}")
                elif t == "design":
                    s = c["scenario"]
                    sc = dict(rps=s["baseRps"], peak=s.get("peakMultiplier", 1.0), read=s["readRatio"],
                              static=s.get("staticRatio", 0.0), budget=s["budget"], lat=s["maxLatencyMs"])
                    start = dict(cdn=0, lb=0, app=1, cache=0, queue=0, shard=1, replica=0)
                    if sim.run(sc, start)["passed"]:
                        err(f"{cid}: starting architecture already passes")
                    sols = sim.search(sc)
                    if not sols:
                        err(f"{cid}: no passing architecture exists")
                    else:
                        best = max(s[1] for s in sols)
                        print(f"  {cid}: {len(sols)} solutions, cheapest ${sols[0][0]}, best rating {best}★")
                else:
                    err(f"{cid}: unknown type {t}")
    print(f"{len(files)} regions, {n_challenges} challenges; choice answer slots {dict(sorted(answer_slots.items()))}")
    if errors:
        print("\n".join("ERROR " + e for e in errors))
        sys.exit(1)
    print("All content valid.")


if __name__ == "__main__":
    main()
