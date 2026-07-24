# BitTorrent P2P File Transfer

A BitTorrent client implemented from scratch in Java (Spring Boot) with a React frontend. It can parse and download `.torrent` files and magnet links, and it includes a **File Hub**: upload any file and the server seeds it over the real BitTorrent peer wire protocol, complete with a generated `.torrent` file, a magnet link, and a built-in HTTP tracker.

## Features

- **Torrent files** — parse metadata (name, info hash, piece hashes), query trackers for peers, and download with concurrent per-peer workers, request pipelining, and SHA-1 piece verification.
- **Magnet links** — full support for `xt` (hex *and* base32 info hashes), `dn`, `tr`, `xs` (exact source), and `x.pe` (direct peers). Metadata is fetched via the `ut_metadata` extension protocol or the `xs` URL.
- **Async downloads with progress** — downloads run server-side in the background; the UI polls piece-level progress and saves the finished file through the browser.
- **File Hub (share & seed)** — uploaded files are split into 256 KB pieces and hashed. The server then:
  - seeds them on a built-in BitTorrent seeder (default port `6881`),
  - answers announces on a built-in HTTP tracker (`/api/tracker/announce`),
  - serves a generated `.torrent` file and a ready-to-use magnet link.

  Paste the magnet link into the Magnet tab (even on another instance) and the file transfers over the actual BitTorrent protocol.
- **Bencode decoder** — paste any bencoded string and get JSON back.
- **Trackers** — HTTP, HTTPS, and UDP trackers are supported, with public fallback trackers for bare magnet links.

## Architecture

```
frontend/            React + TypeScript UI (tabs: Torrent, Magnet, Decode, File Hub)
src/main/java/com/java_torrent/bit_torrent/
  Torrent.java             .torrent (metainfo) parsing model
  TorrentDownloader.java   peer wire protocol client: handshake, piece download, magnet metadata
  TCPService.java          length-prefixed peer message framing
  UdpTrackerService.java   UDP tracker announce (BEP 15)
  TorrentUtils.java        magnet URL parsing, base32, piece hash splitting
  Codec.java               bencode → JSON
  controller/              REST API (torrent, files, tracker)
  service/
    TorrentService.java    torrent/magnet operations
    DownloadManager.java   async download jobs + progress
    FileShareService.java  File Hub storage + .torrent generation
    SeederService.java     BitTorrent seeder for shared files
```

## Running

### Backend (port 8080)

```bash
./gradlew bootRun
```

Environment variables: `PORT` (HTTP port, default 8080), `SEEDER_PORT` (seeder, default 6881).

### Frontend (port 3000)

```bash
cd frontend
npm install
npm start
```

The frontend reads `REACT_APP_API_URL` from `frontend/.env` (default `http://localhost:8080/api/torrent`).

### Docker

```bash
docker build -t bit-torrent .
docker run -p 8080:8080 -p 6881:6881 bit-torrent
```

### Tests

```bash
./gradlew test
```

## REST API

| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/torrent/info` | Parse a `.torrent` file (multipart `file`) |
| POST | `/api/torrent/peers` | Get peer list from trackers |
| POST | `/api/torrent/download/start` | Start async download → `{downloadId, ...}` |
| POST | `/api/torrent/magnet/download/start` | Start async magnet download |
| GET | `/api/torrent/download/status/{id}` | Poll progress (`status`, `progress`, pieces) |
| GET | `/api/torrent/file?path=` | Fetch a completed download (restricted to `downloads/`) |
| POST | `/api/torrent/magnet/parse` | Parse a magnet URL |
| POST | `/api/torrent/magnet/info` | Fetch magnet metadata (xs URL or `ut_metadata`) |
| POST | `/api/torrent/decode` | Decode a bencoded string to JSON |
| POST | `/api/files/upload` | Share a file (starts seeding, returns magnet link) |
| GET | `/api/files/list` | List shared files |
| GET | `/api/files/download/{id}` | Plain HTTP download of a shared file |
| GET | `/api/files/torrent/{id}` | Generated `.torrent` for a shared file |
| GET | `/api/tracker/announce` | Built-in tracker (answers with this server's seeder) |

Synchronous `/api/torrent/download` and `/api/torrent/magnet/download` endpoints also exist; they block until the download finishes.

## CLI

The same jar doubles as a CLI when arguments are passed:

```bash
java -jar build/libs/bit-torrent-0.0.1-SNAPSHOT.jar decode d3:foo3:bare
java -jar build/libs/bit-torrent-0.0.1-SNAPSHOT.jar info sample.torrent
java -jar build/libs/bit-torrent-0.0.1-SNAPSHOT.jar peers sample.torrent
java -jar build/libs/bit-torrent-0.0.1-SNAPSHOT.jar handshake sample.torrent <ip:port>
java -jar build/libs/bit-torrent-0.0.1-SNAPSHOT.jar download -o out.bin sample.torrent
java -jar build/libs/bit-torrent-0.0.1-SNAPSHOT.jar magnet_parse "magnet:?xt=urn:btih:..."
java -jar build/libs/bit-torrent-0.0.1-SNAPSHOT.jar magnet_download -o out.bin "magnet:?..."
```

## Limitations

- Multi-file torrents are downloaded as a single concatenated file.
- The seeder serves only files uploaded through the File Hub.
- No DHT/PEX peer discovery — peers come from trackers, `x.pe` params, or the built-in tracker.
