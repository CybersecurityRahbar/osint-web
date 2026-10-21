#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
╔══════════════════════════════════════════════════════════════════════════════════════════╗
║       ALKASER NEXUS INTELLIGENCE SYSTEM — v54.0  (INTELLIGENCE PLATFORM)                  ║
║                   النظام الاستخباراتي المتكامل — الجيل الخامس                           ║
║  ─────────────────────────────────────────────────────────────────────────────────────  ║
║  • 120+ منصة اجتماعية بكشف دقيق للهوية                                                 ║
║  • كشف صاحب الرقم بدقة عالية (اسم رباعي / ثلاثي)                                       ║
║  • مطابقة صارمة للأسماء الكاملة عبر 15 محرك بحث                                        ║
║  • فحص تسجيل في تليجرام/واتساب/انستجرام/فيسبوك/تيك توك بوقت فعلي                       ║
║  • بحث عكسي متقدم للصور (Yandex + Bing + Google Lens + TinEye + SauceNAO)              ║
║  • تقارير ذكاء اصطناعي عربية باحترافية استخباراتية                                     ║
║  • واجهة DARK MATTER — تصميم استخباراتي مختلف كلياً                                    ║
║  • أرشيف الإنترنت الكامل · شبكة Tor · مواقع التسريب                                   ║
║  • بث فوري إلى تيليجرام · تشغيل عبر ngrok                                              ║
╚══════════════════════════════════════════════════════════════════════════════════════════╝
"""

import tempfile
import os, re, sys, json, time, html, queue, base64, random, socket, string
import hashlib, logging, asyncio, threading, warnings, struct, ipaddress, unicodedata
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple, Set
from urllib.parse import quote_plus, urlparse, urljoin, urlunparse, urlencode, unquote
from concurrent.futures import ThreadPoolExecutor, as_completed
from io import BytesIO

from selenium import webdriver
from selenium.webdriver.chrome.options import Options
from selenium.webdriver.chrome.service import Service
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC
try:
    from webdriver_manager.chrome import ChromeDriverManager
    WDM_AVAILABLE = True
except ImportError:
    WDM_AVAILABLE = False

import requests
from bs4 import BeautifulSoup

warnings.filterwarnings("ignore")
try:
    import urllib3
    urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)
except Exception:
    pass

try:
    import nest_asyncio
    nest_asyncio.apply()
except ImportError:
    pass


try:
    import undetected_chromedriver as uc
except ImportError:
    uc = None

# ═══════════════════════════════════════════════════════════════════════
#  GLOBAL CONFIGURATION
# ═══════════════════════════════════════════════════════════════════════
APP_NAME = "ALKASER OSINT"
APP_VERSION = "54.0-intelligence-platform"
HOST        = "0.0.0.0"
PORT        = int(os.environ.get("PORT", 8765))

AI_API_KEY  = os.environ.get("ALKASER_AI_KEY", "")
AI_MODEL    = "openai/gpt-4o-mini"

BOT_TOKEN   = os.environ.get("ALKASER_TG_BOT",  "")
CHAT_ID     = os.environ.get("ALKASER_TG_CHAT", "")
IMGBB_API_KEY = ""   # مفتاح imgbb الخاص بك
TELEGRAM_ENABLED = True

REPORTS_DIR = Path.home() / "alkaser_nexus_reports"
REPORTS_DIR.mkdir(exist_ok=True)

try:
    LOG_FILE = Path(__file__).parent / "alkaser_nexus.log"
except NameError:
    LOG_FILE = Path.cwd() / "alkaser_nexus.log"

logging.basicConfig(
    filename=str(LOG_FILE), level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
)

USER_AGENTS = [
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36",
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Safari/605.1.15",
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
    "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:125.0) Gecko/20100101 Firefox/125.0",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124.0 YaBrowser/24.4.1 Yowser/2.5 Safari/537.36",
    "Mozilla/5.0 (Android 14; Mobile; rv:109.0) Gecko/119.0 Firefox/119.0",
    "Mozilla/5.0 (Linux; Android 13; SM-S901B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/112.0.0.0 Mobile Safari/537.36",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36 Edg/123.0.0.0",
    "Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:124.0) Gecko/20100101 Firefox/124.0",
]

REQUEST_TIMEOUT = 16
MAX_RETRIES     = 2

# ═══════════════════════════════════════════════════════════════════════
#  HTTP SESSION
# ═══════════════════════════════════════════════════════════════════════
from requests.adapters import HTTPAdapter
session = requests.Session()
session.mount("http://",  HTTPAdapter(pool_connections=60, pool_maxsize=120))
session.mount("https://", HTTPAdapter(pool_connections=60, pool_maxsize=120))


def _headers(extra: Optional[dict] = None) -> dict:
    h = {
        "User-Agent": random.choice(USER_AGENTS),
        "Accept-Language": "en-US,en;q=0.9,ar;q=0.8",
        "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8",
        "Accept-Encoding": "gzip, deflate, br",
        "Connection": "keep-alive",
        "DNT": "1",
        "Sec-Fetch-Dest": "document",
        "Sec-Fetch-Mode": "navigate",
        "Sec-Fetch-Site": "none",
    }
    if extra:
        h.update(extra)
    return h


def safe_get(url, *, timeout=REQUEST_TIMEOUT, headers=None, allow_redirects=True, proxies=None):
    for attempt in range(MAX_RETRIES + 1):
        try:
            r = session.get(url, headers=_headers(headers), timeout=timeout,
                            verify=False, allow_redirects=allow_redirects, proxies=proxies)
            if r.status_code in (200, 301, 302, 404, 403):
                return r
            if r.status_code == 429:
                time.sleep(4 + attempt * 4)
                continue
        except Exception as e:
            logging.debug(f"safe_get fail ({attempt}) {url}: {e}")
        time.sleep(0.6 * (attempt + 1))
    return None


def safe_post(url, *, json_data=None, data=None, files=None, headers=None, timeout=REQUEST_TIMEOUT):
    try:
        return session.post(url, json=json_data, data=data, files=files,
                            headers=_headers(headers), timeout=timeout, verify=False)
    except Exception as e:
        logging.debug(f"safe_post fail {url}: {e}")
        return None


def normalize_url(url: str) -> str:
    if not url:
        return url
    try:
        p = urlparse(url)
        netloc = re.sub(r"^www\.", "", p.netloc)
        q = "&".join(seg for seg in (p.query or "").split("&")
                     if seg and not seg.startswith(("utm_", "fbclid", "gclid", "mc_cid", "mc_eid", "ref=")))
        return urlunparse((p.scheme, netloc, (p.path or "/").rstrip("/") or "/", p.params, q, ""))
    except Exception:
        return url


def slugify(text: str) -> str:
    text = unicodedata.normalize("NFKD", text)
    text = text.encode("ascii", "ignore").decode("ascii")
    return re.sub(r"[^\w\s-]", "", text).strip().lower().replace(" ", "-")


# ═══════════════════════════════════════════════════════════════════════
#  TELEGRAM (unchanged mechanism, enhanced messages)
# ═══════════════════════════════════════════════════════════════════════
class _RateLimiter:
    def __init__(self, rate=18):
        self.rate, self.tokens = rate, rate
        self.last = time.monotonic()
        self.lock = threading.Lock()

    def acquire(self):
        with self.lock:
            now = time.monotonic()
            self.tokens = min(self.rate, self.tokens + (now - self.last) * self.rate)
            self.last = now
            if self.tokens >= 1:
                self.tokens -= 1
                return True
            return False


tg_queue: "queue.Queue[dict]" = queue.Queue(maxsize=3000)
tg_limiter = _RateLimiter(18)


def _tg_worker():
    while True:
        try:
            item = tg_queue.get(timeout=1)
        except queue.Empty:
            continue
        if item is None:
            break
        while not tg_limiter.acquire():
            time.sleep(0.05)
        try:
            t = item["type"]
            if t == "text":
                for i in range(0, len(item["text"]), 4000):
                    safe_post(
                        f"https://api.telegram.org/bot{BOT_TOKEN}/sendMessage",
                        json_data={
                            "chat_id": CHAT_ID,
                            "text": item["text"][i:i + 4000],
                            "parse_mode": "HTML",
                            "disable_web_page_preview": True,
                        }, timeout=10,
                    )
            elif t == "photo":
                try:
                    img = safe_get(item["image_url"], timeout=15)
                    if img and img.content:
                        requests.post(
                            f"https://api.telegram.org/bot{BOT_TOKEN}/sendPhoto",
                            files={"photo": ("img.jpg", img.content)},
                            data={"chat_id": CHAT_ID, "caption": item.get("caption", "")[:1000]},
                            timeout=20, verify=False,
                        )
                except Exception as e:
                    logging.debug(f"tg photo: {e}")
            elif t == "doc":
                try:
                    requests.post(
                        f"https://api.telegram.org/bot{BOT_TOKEN}/sendDocument",
                        files={"document": (item["filename"], item["data"])},
                        data={"chat_id": CHAT_ID, "caption": item.get("caption", "")[:1000]},
                        timeout=40, verify=False,
                    )
                except Exception as e:
                    logging.debug(f"tg doc: {e}")
        except Exception as e:
            logging.debug(f"tg worker: {e}")


if TELEGRAM_ENABLED:
    threading.Thread(target=_tg_worker, daemon=True).start()


def tg_text(msg):
    if not TELEGRAM_ENABLED:
        return
    try:
        tg_queue.put_nowait({"type": "text", "text": msg})
    except queue.Full:
        pass


def tg_photo(image_url, caption=""):
    if not TELEGRAM_ENABLED or not image_url:
        return
    try:
        tg_queue.put_nowait({"type": "photo", "image_url": image_url, "caption": caption})
    except queue.Full:
        pass


def tg_doc(data, filename, caption=""):
    if not TELEGRAM_ENABLED:
        return
    try:
        tg_queue.put_nowait({"type": "doc", "data": data, "filename": filename, "caption": caption})
    except queue.Full:
        pass


# ═══════════════════════════════════════════════════════════════════════
#  STRICT RELEVANCE FILTERS — is_relevant() per mode
# ═══════════════════════════════════════════════════════════════════════
def _norm_text(t: str) -> str:
    """Lowercase + normalize Arabic alef/ya/ta."""
    t = t.lower()
    t = re.sub(r'[أإآا]', 'ا', t)
    t = re.sub(r'[ةه]$', 'ه', t)
    t = re.sub(r'[يى]', 'ي', t)
    return t


def is_relevant_name(result: dict, name: str) -> bool:
    """
    All name parts (len>2) must appear in title+snippet.
    Zero tolerance for partial matches.
    """
    parts = [p for p in name.strip().split() if len(p) > 2]
    if not parts:
        return True
    combined = _norm_text(
        (result.get("title") or "") + " " +
        (result.get("snippet") or "") + " " +
        (result.get("bio") or "") + " " +
        (result.get("name") or "")
    )
    name_norm = _norm_text(name)
    # Check if full name appears verbatim
    if name_norm in combined:
        return True
    # All individual parts must be present
    parts_norm = [_norm_text(p) for p in parts]
    return all(p in combined for p in parts_norm)


def is_relevant_phone(result: dict, phone: str) -> bool:
    """
    Last 7 digits of the phone must appear in the result text.
    """
    digits = re.sub(r"\D", "", phone)
    if len(digits) < 7:
        return True
    last7 = digits[-7:]
    combined = (
        (result.get("title") or "") + " " +
        (result.get("snippet") or "") + " " +
        (result.get("link") or "") + " " +
        (result.get("bio") or "")
    )
    # Check any variant containing last 7 digits
    return last7 in re.sub(r"\D", "", combined)


def is_relevant_email(result: dict, email: str) -> bool:
    """
    Full email address must appear somewhere in the result.
    """
    email_lower = email.strip().lower()
    combined = _norm_text(
        (result.get("title") or "") + " " +
        (result.get("snippet") or "") + " " +
        (result.get("bio") or "") + " " +
        (result.get("link") or "")
    )
    return email_lower in combined


def is_relevant_username(result: dict, username: str) -> bool:
    """
    Username must appear in the result URL or content.
    """
    uname = username.strip().lower()
    link = (result.get("link") or result.get("url") or "").lower()
    combined = _norm_text(
        (result.get("title") or "") + " " +
        (result.get("snippet") or "") + " " +
        (result.get("bio") or "")
    )
    # URL must contain username, OR content must contain @username or username
    return uname in link or uname in combined or f"@{uname}" in combined


def is_relevant_generic(result: dict, query: str) -> bool:
    """Generic relevance: query terms in text."""
    terms = [t.lower() for t in query.strip().split() if len(t) > 2]
    if not terms:
        return True
    combined = (
        (result.get("title") or "") + " " +
        (result.get("snippet") or "") + " " +
        (result.get("link") or "")
    ).lower()
    return any(t in combined for t in terms)


def apply_relevance_filter(results: List[dict], query: str, mode: str) -> List[dict]:
    """Apply strict relevance filter based on mode."""
    if not query:
        return results
    filtered = []
    for r in results:
        try:
            if mode == "name":
                if is_relevant_name(r, query):
                    filtered.append(r)
            elif mode == "phone":
                if is_relevant_phone(r, query):
                    filtered.append(r)
            elif mode == "email":
                if is_relevant_email(r, query):
                    filtered.append(r)
            elif mode == "username":
                if is_relevant_username(r, query):
                    filtered.append(r)
            else:
                if is_relevant_generic(r, query):
                    filtered.append(r)
        except Exception:
            filtered.append(r)
    return filtered


# ═══════════════════════════════════════════════════════════════════════
#  DUCKDUCKGO
# ═══════════════════════════════════════════════════════════════════════
DUCKDUCKGO_AVAILABLE = False
try:
    from ddgs import DDGS
    DUCKDUCKGO_AVAILABLE = True
except ImportError:
    try:
        from duckduckgo_search import DDGS
        DUCKDUCKGO_AVAILABLE = True
    except ImportError:
        DDGS = None


class DDG:
    @staticmethod
    def text(query, max_results=60):
        out = []
        if not DUCKDUCKGO_AVAILABLE:
            return out
        try:
            with DDGS() as ddgs:
                for r in ddgs.text(query, max_results=max_results):
                    out.append({
                        "source": "DuckDuckGo",
                        "title": r.get("title", ""),
                        "link": r.get("href", ""),
                        "snippet": (r.get("body") or "")[:500],
                        "type": "web",
                    })
        except Exception as e:
            logging.debug(f"DDG text: {e}")
        return out

    @staticmethod
    def images(query, max_results=40):
        out = []
        if not DUCKDUCKGO_AVAILABLE:
            return out
        try:
            with DDGS() as ddgs:
                for r in ddgs.images(query, max_results=max_results):
                    if r.get("image"):
                        out.append({
                            "source": "DuckDuckGo Images",
                            "title": r.get("title", "Image"),
                            "image_url": r["image"],
                            "thumbnail": r.get("thumbnail", ""),
                            "link": r.get("url", r["image"]),
                            "type": "image",
                        })
        except Exception as e:
            logging.debug(f"DDG img: {e}")
        return out

    @staticmethod
    def videos(query, max_results=30):
        out = []
        if not DUCKDUCKGO_AVAILABLE:
            return out
        try:
            with DDGS() as ddgs:
                for r in ddgs.videos(query, max_results=max_results):
                    out.append({
                        "source": "DuckDuckGo Videos",
                        "title": r.get("title", ""),
                        "link": r.get("content") or r.get("url", ""),
                        "thumbnail": r.get("images", {}).get("small", "") if isinstance(r.get("images"), dict) else "",
                        "snippet": (r.get("description") or "")[:300],
                        "type": "video",
                    })
        except Exception as e:
            logging.debug(f"DDG vid: {e}")
        return out

    @staticmethod
    def news(query, max_results=30):
        out = []
        if not DUCKDUCKGO_AVAILABLE:
            return out
        try:
            with DDGS() as ddgs:
                for r in ddgs.news(query, max_results=max_results):
                    out.append({
                        "source": "DuckDuckGo News",
                        "title": r.get("title", ""),
                        "link": r.get("url", ""),
                        "snippet": (r.get("body") or "")[:400],
                        "date": r.get("date", ""),
                        "type": "news",
                    })
        except Exception as e:
            logging.debug(f"DDG news: {e}")
        return out


# ═══════════════════════════════════════════════════════════════════════
#  SEARCH ENGINES
# ═══════════════════════════════════════════════════════════════════════
class Bing:
    @staticmethod
    def search(query, max_results=30):
        out = []
        try:
            r = safe_get(f"https://www.bing.com/search?q={quote_plus(query)}&count={max_results}&setlang=en")
            if not r:
                return out
            soup = BeautifulSoup(r.text, "html.parser")
            for li in soup.select("li.b_algo")[:max_results]:
                h2 = li.select_one("h2 a")
                p = li.select_one("p, .b_caption p")
                if h2:
                    out.append({
                        "source": "Bing",
                        "title": h2.get_text(strip=True),
                        "link": h2.get("href", ""),
                        "snippet": (p.get_text(strip=True) if p else "")[:500],
                        "type": "web",
                    })
        except Exception as e:
            logging.debug(f"Bing: {e}")
        return out

    @staticmethod
    def images(query, max_results=20):
        out = []
        try:
            r = safe_get(f"https://www.bing.com/images/search?q={quote_plus(query)}&FORM=HDRSC3")
            if not r:
                return out
            soup = BeautifulSoup(r.text, "html.parser")
            for item in soup.select("a.iusc")[:max_results]:
                try:
                    m_str = item.get("m", "")
                    if m_str:
                        m = json.loads(m_str)
                        out.append({
                            "source": "Bing Images",
                            "title": m.get("t", ""),
                            "image_url": m.get("murl", ""),
                            "thumbnail": m.get("turl", ""),
                            "link": m.get("purl", ""),
                            "type": "image",
                        })
                except Exception:
                    pass
        except Exception as e:
            logging.debug(f"Bing images: {e}")
        return out


class Yandex:
    @staticmethod
    def search(query, max_results=25):
        out = []
        try:
            r = safe_get(f"https://yandex.com/search/?text={quote_plus(query)}&lr=84")
            if not r:
                return out
            soup = BeautifulSoup(r.text, "html.parser")
            for item in soup.select("li.serp-item, div.serp-item")[:max_results]:
                h2 = item.select_one("h2 a, a.OrganicTitle-Link")
                desc = item.select_one("div.OrganicTextContentSpan, .text-container, .Organic-ContentWrapper")
                if h2:
                    out.append({
                        "source": "Yandex",
                        "title": h2.get_text(strip=True),
                        "link": h2.get("href", ""),
                        "snippet": (desc.get_text(strip=True) if desc else "")[:500],
                        "type": "web",
                    })
        except Exception as e:
            logging.debug(f"Yandex: {e}")
        return out


class Brave:
    @staticmethod
    def search(query, max_results=25):
        out = []
        try:
            r = safe_get(f"https://search.brave.com/search?q={quote_plus(query)}&source=web")
            if not r:
                return out
            soup = BeautifulSoup(r.text, "html.parser")
            for res in soup.select("div.snippet, div[data-type=web]")[:max_results]:
                a = res.select_one("a.result-header, a.heading")
                title_el = res.select_one(".title, .snippet-title, h3")
                desc = res.select_one(".snippet-description, .snippet-content, .body")
                if a and title_el:
                    out.append({
                        "source": "Brave",
                        "title": title_el.get_text(strip=True),
                        "link": a.get("href", ""),
                        "snippet": (desc.get_text(strip=True) if desc else "")[:500],
                        "type": "web",
                    })
        except Exception as e:
            logging.debug(f"Brave: {e}")
        return out


class Mojeek:
    @staticmethod
    def search(query, max_results=25):
        out = []
        try:
            r = safe_get(f"https://www.mojeek.com/search?q={quote_plus(query)}&si=1")
            if not r:
                return out
            soup = BeautifulSoup(r.text, "html.parser")
            for li in soup.select("ul.results-standard li")[:max_results]:
                a = li.select_one("a.ob")
                desc = li.select_one("p.s")
                if a:
                    out.append({
                        "source": "Mojeek",
                        "title": a.get_text(strip=True),
                        "link": a.get("href", ""),
                        "snippet": (desc.get_text(strip=True) if desc else "")[:500],
                        "type": "web",
                    })
        except Exception as e:
            logging.debug(f"Mojeek: {e}")
        return out


class Qwant:
    @staticmethod
    def search(query, max_results=25):
        out = []
        try:
            r = safe_get(
                f"https://api.qwant.com/v3/search/web?q={quote_plus(query)}&count={max_results}&locale=en_us&safesearch=0",
                headers={"Accept": "application/json"},
            )
            if not r:
                return out
            j = r.json()
            items = ((j.get("data") or {}).get("result") or {}).get("items") or {}
            mainline = items.get("mainline") if isinstance(items, dict) else None
            candidates = []
            if isinstance(mainline, list):
                for section in mainline:
                    if isinstance(section, dict) and section.get("items"):
                        candidates.extend(section["items"])
            elif isinstance(items, list):
                candidates = items
            for it in candidates[:max_results]:
                if not isinstance(it, dict):
                    continue
                out.append({
                    "source": "Qwant",
                    "title": (it.get("title") or "")[:200],
                    "link": it.get("url", ""),
                    "snippet": (it.get("desc") or "")[:500],
                    "type": "web",
                })
        except Exception as e:
            logging.debug(f"Qwant: {e}")
        return out


class Startpage:
    @staticmethod
    def search(query, max_results=20):
        out = []
        try:
            r = safe_get(f"https://www.startpage.com/do/search?q={quote_plus(query)}&cat=web")
            if not r:
                return out
            soup = BeautifulSoup(r.text, "html.parser")
            for res in soup.select("div.w-gl__result, article.result")[:max_results]:
                a = res.select_one("a.w-gl__result-title, a.result-link")
                desc = res.select_one("p.w-gl__description, p.description")
                if a:
                    out.append({
                        "source": "Startpage",
                        "title": a.get_text(strip=True),
                        "link": a.get("href", ""),
                        "snippet": (desc.get_text(strip=True) if desc else "")[:500],
                        "type": "web",
                    })
        except Exception as e:
            logging.debug(f"Startpage: {e}")
        return out


class Searx:
    INSTANCES = [
        "https://searx.be", "https://search.disroot.org",
        "https://searx.tiekoetter.com", "https://searx.oloke.xyz",
    ]

    @staticmethod
    def search(query, max_results=20):
        out = []
        for inst in Searx.INSTANCES:
            try:
                r = safe_get(
                    f"{inst}/search?q={quote_plus(query)}&format=json&engines=google,bing,duckduckgo",
                    headers={"Accept": "application/json"}, timeout=10,
                )
                if not r or r.status_code != 200:
                    continue
                for item in (r.json().get("results") or [])[:max_results]:
                    out.append({
                        "source": "SearXNG",
                        "title": item.get("title", ""),
                        "link": item.get("url", ""),
                        "snippet": (item.get("content") or "")[:500],
                        "type": "web",
                    })
                if out:
                    break
            except Exception as e:
                logging.debug(f"Searx {inst}: {e}")
        return out


class Ecosia:
    @staticmethod
    def search(query, max_results=20):
        out = []
        try:
            r = safe_get(f"https://www.ecosia.org/search?q={quote_plus(query)}")
            if not r:
                return out
            soup = BeautifulSoup(r.text, "html.parser")
            for res in soup.select("div.result, article.result")[:max_results]:
                a = res.select_one("a.result-title, a[data-test-id='result-link']")
                desc = res.select_one("p.result-snippet, p.snippet")
                if a:
                    out.append({
                        "source": "Ecosia",
                        "title": a.get_text(strip=True),
                        "link": a.get("href", ""),
                        "snippet": (desc.get_text(strip=True) if desc else "")[:400],
                        "type": "web",
                    })
        except Exception as e:
            logging.debug(f"Ecosia: {e}")
        return out


class Wikipedia:
    @staticmethod
    def search(query, max_results=10):
        out = []
        try:
            r = safe_get(
                f"https://en.wikipedia.org/w/api.php?action=query&list=search&srsearch={quote_plus(query)}&utf8=&format=json&srlimit={max_results}",
                headers={"Accept": "application/json"},
            )
            if not r:
                return out
            for item in (r.json().get("query", {}).get("search") or []):
                title = item.get("title", "")
                out.append({
                    "source": "Wikipedia",
                    "title": title,
                    "link": f"https://en.wikipedia.org/wiki/{quote_plus(title.replace(' ', '_'))}",
                    "snippet": re.sub(r"<[^>]+>", "", item.get("snippet", ""))[:500],
                    "type": "web",
                })
        except Exception as e:
            logging.debug(f"Wikipedia: {e}")
        return out


class RedditAPI:
    @staticmethod
    def search(query, max_results=15):
        out = []
        try:
            r = safe_get(
                f"https://www.reddit.com/search.json?q={quote_plus(query)}&limit={max_results}&sort=relevance",
                headers={"User-Agent": random.choice(USER_AGENTS)},
            )
            if not r or r.status_code != 200:
                return out
            for child in (r.json().get("data", {}).get("children") or []):
                d = child.get("data", {})
                out.append({
                    "source": "Reddit Search",
                    "title": d.get("title", ""),
                    "link": f"https://reddit.com{d.get('permalink', '')}",
                    "snippet": (d.get("selftext") or "")[:400],
                    "type": "web",
                })
        except Exception as e:
            logging.debug(f"RedditAPI: {e}")
        return out


class Lemmy:
    @staticmethod
    def search(query, max_results=10):
        out = []
        try:
            r = safe_get(
                f"https://lemmy.world/api/v3/search?q={quote_plus(query)}&type_=All&limit={max_results}",
                headers={"Accept": "application/json"},
            )
            if not r or r.status_code != 200:
                return out
            j = r.json()
            for p in (j.get("posts") or [])[:max_results]:
                pd = p.get("post", {})
                out.append({
                    "source": "Lemmy",
                    "title": pd.get("name", ""),
                    "link": pd.get("ap_id", pd.get("url", "")),
                    "snippet": (pd.get("body") or "")[:400],
                    "type": "web",
                })
        except Exception as e:
            logging.debug(f"Lemmy: {e}")
        return out


class Mastodon:
    @staticmethod
    def search(query, max_results=10):
        out = []
        instances = ["mastodon.social", "fosstodon.org"]
        for inst in instances:
            try:
                r = safe_get(
                    f"https://{inst}/api/v2/search?q={quote_plus(query)}&limit={max_results}",
                    headers={"Accept": "application/json"},
                )
                if not r or r.status_code != 200:
                    continue
                j = r.json()
                for st in (j.get("statuses") or [])[:max_results]:
                    content = re.sub(r"<[^>]+>", "", st.get("content", ""))
                    acc = st.get("account", {})
                    out.append({
                        "source": "Mastodon",
                        "title": f"@{acc.get('acct', '')}",
                        "link": st.get("url", ""),
                        "snippet": content[:400],
                        "avatar": acc.get("avatar", ""),
                        "thumbnail": acc.get("avatar", ""),
                        "type": "social",
                    })
                if out:
                    break
            except Exception as e:
                logging.debug(f"Mastodon {inst}: {e}")
        return out


class Ahmia:
    @staticmethod
    def search(query, max_results=15):
        out = []
        try:
            r = safe_get(f"https://ahmia.fi/search/?q={quote_plus(query)}", timeout=20)
            if not r or r.status_code != 200:
                return out
            soup = BeautifulSoup(r.text, "html.parser")
            for res in soup.select("li.result, div.result")[:max_results]:
                a = res.select_one("a")
                desc = res.select_one("p, .description")
                if a:
                    out.append({
                        "source": "Tor/Ahmia",
                        "title": a.get_text(strip=True),
                        "link": a.get("href", ""),
                        "snippet": (desc.get_text(strip=True) if desc else "")[:400],
                        "type": "dark",
                    })
        except Exception as e:
            logging.debug(f"Ahmia: {e}")
        return out


# ═══════════════════════════════════════════════════════════════════════
#  WAYBACK MACHINE & ARCHIVE.ORG
# ═══════════════════════════════════════════════════════════════════════
class WaybackMachine:
    """Internet Archive Wayback — strict archive-only retrieval (text + media)."""

    @staticmethod
    def _cdx(url_pattern, max_results=40, filters=None):
        out = []
        try:
            base = (f"https://web.archive.org/cdx/search/cdx?url={quote_plus(url_pattern)}"
                    f"&output=json&fl=original,statuscode,timestamp,mimetype,length"
                    f"&limit={max_results}&filter=statuscode:200&collapse=urlkey")
            for f in (filters or []):
                base += f"&filter={quote_plus(f)}"
            r = safe_get(base, headers={"Accept": "application/json"}, timeout=18)
            if not r or r.status_code != 200:
                return out
            rows = r.json()
            if not rows or len(rows) <= 1:
                return out
            for row in rows[1:]:
                if len(row) < 4:
                    continue
                orig, _, ts, mime = row[0], row[1], row[2], row[3]
                dt = f"{ts[:4]}-{ts[4:6]}-{ts[6:8]}" if len(ts) >= 8 else ts
                out.append((orig, ts, dt, mime))
        except Exception as e:
            logging.debug(f"wayback cdx: {e}")
        return out

    @staticmethod
    def search_text(query, max_results=40):
        out = []
        # Try multiple url patterns to maximize archive coverage
        patterns = [f"*{query}*", f"*/{query}*", f"*{query}/*"]
        seen = set()
        for pat in patterns:
            for orig, ts, dt, mime in WaybackMachine._cdx(pat, max_results=max_results,
                                                          filters=["mimetype:text/html"]):
                key = (orig, ts[:8])
                if key in seen:
                    continue
                seen.add(key)
                out.append({
                    "source": "Wayback Machine",
                    "title": f"[ARCHIVE] {orig[:140]}",
                    "link": f"https://web.archive.org/web/{ts}/{orig}",
                    "snippet": f"Archived snapshot from {dt} — {orig[:240]}",
                    "date": dt, "type": "archive",
                })
                if len(out) >= max_results:
                    return out
        return out

    @staticmethod
    def search_media(query, max_results=30):
        """Pull image/video/audio snapshots from Wayback CDX."""
        media = []
        configs = [
            ("image",  ["mimetype:image/jpeg", "mimetype:image/png", "mimetype:image/gif", "mimetype:image/webp"]),
            ("video",  ["mimetype:video/mp4",  "mimetype:video/webm", "mimetype:video/x-msvideo"]),
            ("audio",  ["mimetype:audio/mpeg", "mimetype:audio/mp3",  "mimetype:audio/wav", "mimetype:audio/ogg"]),
        ]
        for kind, mime_filters in configs:
            for mf in mime_filters:
                for orig, ts, dt, mime in WaybackMachine._cdx(f"*{query}*",
                                                              max_results=max_results,
                                                              filters=[mf]):
                    raw = f"https://web.archive.org/web/{ts}id_/{orig}"
                    item = {
                        "source": "Wayback Media",
                        "title": f"[ARCHIVE-{kind.upper()}] {orig.split('/')[-1][:120] or orig[:120]}",
                        "link": f"https://web.archive.org/web/{ts}/{orig}",
                        "snippet": f"Archived {kind} ({mime}) from {dt} — {orig[:200]}",
                        "date": dt, "type": f"archive_{kind}",
                        "media_url": raw, "mime": mime,
                    }
                    if kind == "image":
                        item["image"] = raw
                    elif kind == "video":
                        item["video"] = raw
                    elif kind == "audio":
                        item["audio"] = raw
                    media.append(item)
                    if len(media) >= max_results * 3:
                        return media
        return media


class ArchiveOrg:
    """archive.org metadata search — fetches texts, movies, audio, images."""

    @staticmethod
    def search(query, max_results=40):
        out = []
        try:
            r = safe_get(
                f"https://archive.org/advancedsearch.php?q={quote_plus(query)}"
                f"&rows={max_results}&output=json"
                f"&fl[]=identifier&fl[]=title&fl[]=description&fl[]=mediatype"
                f"&fl[]=creator&fl[]=date&fl[]=subject",
                headers={"Accept": "application/json"}, timeout=16,
            )
            if not r or r.status_code != 200:
                return out
            for doc in (r.json().get("response", {}).get("docs") or []):
                ident = doc.get("identifier", "")
                mtype = (doc.get("mediatype") or "text").lower()
                kind_map = {"movies": "archive_video", "audio": "archive_audio",
                            "image": "archive_image", "texts": "archive_text",
                            "etree": "archive_audio", "software": "archive"}
                t = kind_map.get(mtype, "archive")
                item = {
                    "source": "Archive.org",
                    "title": f"[ARCHIVE] {doc.get('title','') or ident}",
                    "link": f"https://archive.org/details/{ident}",
                    "snippet": (str(doc.get("description") or "")[:500]
                                + f"  ·  type={mtype}"
                                + (f"  ·  by {doc.get('creator')}" if doc.get('creator') else "")),
                    "date": str(doc.get("date") or ""),
                    "type": t,
                }
                # Direct media URLs from archive.org download endpoint
                if mtype == "image":
                    item["image"] = f"https://archive.org/services/img/{ident}"
                elif mtype == "movies":
                    item["video"] = f"https://archive.org/download/{ident}/{ident}.mp4"
                    item["image"] = f"https://archive.org/services/img/{ident}"
                elif mtype in ("audio", "etree"):
                    item["audio"] = f"https://archive.org/download/{ident}/{ident}.mp3"
                elif mtype == "texts":
                    item["text_url"] = f"https://archive.org/stream/{ident}/{ident}_djvu.txt"
                out.append(item)
        except Exception as e:
            logging.debug(f"archive.org: {e}")
        return out

    @staticmethod
    def fetch_text(identifier, max_chars=20000):
        """Fetch full plain text of an archive.org texts item."""
        try:
            r = safe_get(f"https://archive.org/stream/{identifier}/{identifier}_djvu.txt",
                         timeout=20)
            if r and r.status_code == 200:
                return r.text[:max_chars]
        except Exception as e:
            logging.debug(f"archive.org text: {e}")
        return ""


# ═══════════════════════════════════════════════════════════════════════
#  PASTE & LEAK SITES
# ═══════════════════════════════════════════════════════════════════════
class PasteSites:
    @staticmethod
    def search(query, max_results=15):
        out = []
        dorks = [
            f'site:pastebin.com "{query}"',
            f'site:paste.ee "{query}"',
            f'site:ghostbin.com "{query}"',
            f'site:hastebin.com "{query}"',
            f'site:controlc.com "{query}"',
            f'"{query}" password leaked',
            f'"{query}" breach database',
            f'"{query}" "have been pwned"',
        ]
        for dork in dorks:
            try:
                results = DDG.text(dork, max_results=8)
                for r in results:
                    r["type"] = "leak"
                    r["source"] = "Leak DB Search"
                    out.append(r)
                time.sleep(0.3)
            except Exception:
                pass
        return out[:30]


# ═══════════════════════════════════════════════════════════════════════
#  DORK ENGINE
# ═══════════════════════════════════════════════════════════════════════
class DorkEngine:
    @staticmethod
    def dorks_for_name(name: str) -> List[str]:
        q = f'"{name}"'
        parts = name.strip().split()
        dorks = [
            q,
            f'{q} site:linkedin.com',
            f'{q} site:facebook.com',
            f'{q} site:twitter.com OR site:x.com',
            f'{q} site:instagram.com',
            f'{q} filetype:pdf',
            f'{q} site:wikipedia.org',
        ]
        if len(parts) >= 2:
            dorks.append(f'"{parts[0]}" "{parts[-1]}"')
        return dorks

    @staticmethod
    def dorks_for_email(email: str) -> List[str]:
        local, _, domain = email.partition("@")
        return [
            f'"{email}"',
            f'"{email}" password OR leaked OR breach',
            f'site:pastebin.com "{email}"',
            f'"{local}" site:{domain}',
            f'"{email}" profile',
        ]

    @staticmethod
    def dorks_for_phone(phone: str) -> List[str]:
        digits = re.sub(r"\D", "", phone)
        return [
            f'"{phone}"',
            f'"{digits}"',
            f'"+{digits}"',
            f'"{phone}" name OR owner OR caller',
            f'site:truepeoplesearch.com "{phone}"',
            f'site:whitepages.com "{phone}"',
        ]

    @staticmethod
    def dorks_for_domain(domain: str) -> List[str]:
        return [
            f'site:{domain}',
            f'"{domain}" email OR contact',
            f'"{domain}" admin OR login',
            f'site:{domain} filetype:pdf',
            f'"{domain}" leak OR breach',
        ]

    @staticmethod
    def run(dork: str, per_engine: int = 10) -> List[dict]:
        out = []
        seen = set()
        engines = [DDG.text, Bing.search, Brave.search]
        for fn in engines:
            try:
                for r in fn(dork, per_engine):
                    link = normalize_url(r.get("link", ""))
                    if link and link not in seen:
                        seen.add(link)
                        out.append(r)
            except Exception:
                pass
        return out


# ═══════════════════════════════════════════════════════════════════════
#  SOCIAL PLATFORM OSINT — Username Scan
# ═══════════════════════════════════════════════════════════════════════
SOCIAL_PLATFORMS = [
    # (name, url_template, check_pattern)
    ("Instagram",    "https://www.instagram.com/{}/",              ["og:title", "ProfilePage"]),
    ("Twitter/X",    "https://x.com/{}",                           ["og:title", "twitter:title"]),
    ("TikTok",       "https://www.tiktok.com/@{}",                 ["og:title", "tiktok"]),
    ("GitHub",       "https://github.com/{}",                      ["og:title", "GitHub"]),
    ("Reddit",       "https://www.reddit.com/user/{}/",            ["og:title", "reddit"]),
    ("LinkedIn",     "https://www.linkedin.com/in/{}/",            ["og:title", "LinkedIn"]),
    ("Pinterest",    "https://www.pinterest.com/{}/",              ["og:title", "Pinterest"]),
    ("Telegram",     "https://t.me/{}",                            ["tgme_page_title"]),
    ("YouTube",      "https://www.youtube.com/@{}",                ["og:title", "YouTube"]),
    ("Twitch",       "https://www.twitch.tv/{}",                   ["og:title", "Twitch"]),
    ("Snapchat",     "https://www.snapchat.com/add/{}",            ["og:title", "Snapchat"]),
    ("Spotify",      "https://open.spotify.com/user/{}",           ["og:title", "Spotify"]),
    ("SoundCloud",   "https://soundcloud.com/{}",                  ["og:title", "SoundCloud"]),
    ("DevTo",        "https://dev.to/{}",                          ["og:title", "DEV"]),
    ("Medium",       "https://medium.com/@{}",                     ["og:title", "Medium"]),
    ("Quora",        "https://www.quora.com/profile/{}",           ["og:title", "Quora"]),
    ("Flickr",       "https://www.flickr.com/people/{}",           ["og:title", "Flickr"]),
    ("VK",           "https://vk.com/{}",                          ["og:title", "VK"]),
    ("OK",           "https://ok.ru/{}",                           ["og:title", "ok.ru"]),
    ("Behance",      "https://www.behance.net/{}",                 ["og:title", "Behance"]),
    ("Dribbble",     "https://dribbble.com/{}",                    ["og:title", "Dribbble"]),
    ("HackerNews",   "https://news.ycombinator.com/user?id={}",   ["og:title", "Hacker News"]),
    ("Keybase",      "https://keybase.io/{}",                      ["og:title", "Keybase"]),
    ("Gitlab",       "https://gitlab.com/{}",                      ["og:title", "GitLab"]),
    ("Bitbucket",    "https://bitbucket.org/{}/",                  ["og:title", "Bitbucket"]),
    ("Steam",        "https://steamcommunity.com/id/{}",           ["og:title", "Steam"]),
    ("Patreon",      "https://www.patreon.com/{}",                 ["og:title", "Patreon"]),
    ("Etsy",         "https://www.etsy.com/people/{}",             ["og:title", "Etsy"]),
    ("Cash.me",      "https://cash.me/${}",                        ["og:title", "Cash App"]),
    ("About.me",     "https://about.me/{}",                        ["og:title", "about.me"]),
    ("Gravatar",     "https://en.gravatar.com/{}",                 ["og:title", "Gravatar"]),
    ("HackerOne",    "https://hackerone.com/{}",                   ["og:title", "HackerOne"]),
    ("Bugcrowd",     "https://bugcrowd.com/{}",                    ["og:title", "Bugcrowd"]),
    ("CodePen",      "https://codepen.io/{}",                      ["og:title", "CodePen"]),
    ("JSFiddle",     "https://jsfiddle.net/{}/",                   ["og:title", "JSFiddle"]),
    ("Tumblr",       "https://{}.tumblr.com/",                     ["og:title", "Tumblr"]),
    ("WordPress",    "https://{}.wordpress.com/",                  ["og:title", "WordPress"]),
    ("Wix",          "https://www.wix.com/about/{}",               ["og:title", "Wix"]),
    ("Mixcloud",     "https://www.mixcloud.com/{}/",               ["og:title", "Mixcloud"]),
    ("Vimeo",        "https://vimeo.com/{}",                       ["og:title", "Vimeo"]),
    ("Dailymotion",  "https://www.dailymotion.com/{}",             ["og:title", "Dailymotion"]),
    ("Replit",       "https://replit.com/@{}",                     ["og:title", "Replit"]),
    ("Trello",       "https://trello.com/{}",                      ["og:title", "Trello"]),
    ("ProductHunt",  "https://www.producthunt.com/@{}",            ["og:title", "Product Hunt"]),
    ("AngelList",    "https://angel.co/u/{}",                      ["og:title", "AngelList"]),
    ("DockerHub",    "https://hub.docker.com/u/{}",                ["og:title", "Docker Hub"]),
    ("PyPI",         "https://pypi.org/user/{}",                   ["og:title", "PyPI"]),
    ("NPM",          "https://www.npmjs.com/~{}",                  ["og:title", "npm"]),
    ("Ravelry",      "https://www.ravelry.com/people/{}",          ["og:title", "Ravelry"]),
    ("GoodReads",    "https://www.goodreads.com/{}",               ["og:title", "Goodreads"]),
    ("LastFM",       "https://www.last.fm/user/{}",                ["og:title", "Last.fm"]),
    ("RateYourMusic","https://rateyourmusic.com/~{}",              ["og:title", "Rate Your Music"]),
    ("Letterboxd",   "https://letterboxd.com/{}/",                 ["og:title", "Letterboxd"]),
    ("Itch.io",      "https://{}.itch.io/",                        ["og:title", "itch.io"]),
    ("Bandcamp",     "https://{}.bandcamp.com/",                   ["og:title", "Bandcamp"]),
    ("SlideShare",   "https://www.slideshare.net/{}",              ["og:title", "SlideShare"]),
    ("ResearchGate", "https://www.researchgate.net/profile/{}",    ["og:title", "ResearchGate"]),
    ("Academia",     "https://independent.academia.edu/{}",        ["og:title", "Academia.edu"]),
    ("Xing",         "https://www.xing.com/profile/{}",            ["og:title", "XING"]),
    ("OKCupid",      "https://www.okcupid.com/profile/{}",         ["og:title", "OkCupid"]),
    ("Kik",          "https://kik.me/{}",                          ["og:title", "Kik"]),
    ("Line",         "https://line.me/ti/p/~{}",                   ["og:title", "LINE"]),
    ("Skype",        "https://join.skype.com/invite/{}",           ["og:title", "Skype"]),
    ("Discord",      "https://discord.com/users/{}",               ["og:title", "Discord"]),
    ("Slack",        "https://{}.slack.com",                       ["og:title", "Slack"]),
    ("Mastodon",     "https://mastodon.social/@{}",                ["og:title", "Mastodon"]),
    ("Threads",      "https://www.threads.net/@{}",                ["og:title", "Threads"]),
    ("Bluesky",      "https://bsky.app/profile/{}.bsky.social",   ["og:title", "Bluesky"]),
    ("PixelFed",     "https://pixelfed.social/@{}",                ["og:title", "Pixelfed"]),
    ("Peertube",     "https://framatube.org/a/{}/",                ["og:title", "PeerTube"]),
    ("Weibo",        "https://weibo.com/{}",                       ["og:title", "Weibo"]),
    ("Zhihu",        "https://www.zhihu.com/people/{}",            ["og:title", "Zhihu"]),
    ("Bilibili",     "https://space.bilibili.com/{}",              ["og:title", "Bilibili"]),
    ("CrazyGames",   "https://www.crazygames.com/profile/{}",      ["og:title", "CrazyGames"]),
    ("Kongregate",   "https://www.kongregate.com/accounts/{}",     ["og:title", "Kongregate"]),
    ("Xbox",         "https://account.xbox.com/en-US/profile?gamertag={}", ["og:title", "Xbox"]),
    ("PSN",          "https://psnprofiles.com/{}",                 ["og:title", "PSN"]),
    ("Clubhouse",    "https://www.joinclubhouse.com/@{}",          ["og:title", "Clubhouse"]),
    ("Substack",     "https://{}.substack.com/",                   ["og:title", "Substack"]),
    ("Hashnode",     "https://hashnode.com/@{}",                   ["og:title", "Hashnode"]),
    ("Buy Me Coffee","https://www.buymeacoffee.com/{}",            ["og:title", "Buy Me a Coffee"]),
    ("Ko-fi",        "https://ko-fi.com/{}",                       ["og:title", "Ko-fi"]),
    ("Gumroad",      "https://gumroad.com/{}",                     ["og:title", "Gumroad"]),
    ("Fiverr",       "https://www.fiverr.com/{}",                  ["og:title", "Fiverr"]),
    ("Upwork",       "https://www.upwork.com/freelancers/~{}",     ["og:title", "Upwork"]),
    ("Freelancer",   "https://www.freelancer.com/u/{}",            ["og:title", "Freelancer"]),
    ("Toptal",       "https://www.toptal.com/resume/{}",           ["og:title", "Toptal"]),
    ("Guru",         "https://www.guru.com/freelancers/{}",        ["og:title", "Guru"]),
    ("PeoplePerHour","https://www.peopleperhour.com/freelancer/{}",["og:title", "PeoplePerHour"]),
    ("99designs",    "https://99designs.com/profiles/{}",          ["og:title", "99designs"]),
    ("TrustPilot",   "https://www.trustpilot.com/users/{}",        ["og:title", "Trustpilot"]),
    ("Yelp",         "https://www.yelp.com/user_details?userid={}",["og:title", "Yelp"]),
    ("Foursquare",   "https://foursquare.com/user/{}",             ["og:title", "Foursquare"]),
    ("Tripadvisor",  "https://www.tripadvisor.com/members/{}",     ["og:title", "TripAdvisor"]),
    ("Airbnb",       "https://www.airbnb.com/users/show/{}",       ["og:title", "Airbnb"]),
    ("Couchsurfing", "https://www.couchsurfing.com/people/{}",     ["og:title", "Couchsurfing"]),
    ("GaiaGPS",      "https://www.gaiagps.com/user/{}/",           ["og:title", "Gaia GPS"]),
    ("AllTrails",    "https://www.alltrails.com/members/{}",       ["og:title", "AllTrails"]),
    ("Strava",       "https://www.strava.com/athletes/{}",         ["og:title", "Strava"]),
    ("Duolingo",     "https://www.duolingo.com/profile/{}",        ["og:title", "Duolingo"]),
    ("Lichess",      "https://lichess.org/@/{}",                   ["og:title", "Lichess"]),
    ("Chess.com",    "https://www.chess.com/member/{}",            ["og:title", "Chess.com"]),
    ("Instructables","https://www.instructables.com/member/{}",    ["og:title", "Instructables"]),
    ("Hackaday.io",  "https://hackaday.io/{}",                     ["og:title", "Hackaday.io"]),
    ("HuggingFace",  "https://huggingface.co/{}",                  ["og:title", "Hugging Face"]),
    ("Kaggle",       "https://www.kaggle.com/{}",                  ["og:title", "Kaggle"]),
    ("Scratch",      "https://scratch.mit.edu/users/{}/",          ["og:title", "Scratch"]),
    ("Khan Academy", "https://www.khanacademy.org/profile/{}",     ["og:title", "Khan Academy"]),
    ("Coursera",     "https://www.coursera.org/user/{}",           ["og:title", "Coursera"]),
    ("Udemy",        "https://www.udemy.com/user/{}/",             ["og:title", "Udemy"]),
]


def _check_platform(name: str, url_tpl: str, patterns: List[str], username: str) -> Optional[dict]:
    url = url_tpl.format(username)
    try:
        r = safe_get(url, timeout=12)
        if not r or r.status_code not in (200, 301, 302):
            return None
        body = r.text
        # Must contain at least one pattern
        found = any(p.lower() in body.lower() for p in patterns)
        if not found:
            return None

        # Verify it's actually the user's profile (username should appear in body)
        if username.lower() not in body.lower():
            return None

        # Extract metadata
        soup = BeautifulSoup(body, "html.parser")
        og_title = soup.find("meta", property="og:title")
        og_desc = soup.find("meta", property="og:description")
        og_img = soup.find("meta", property="og:image")
        title_tag = soup.find("title")

        title = (og_title.get("content", "") if og_title else "") or (title_tag.get_text(strip=True) if title_tag else "")
        desc = og_desc.get("content", "") if og_desc else ""
        avatar = og_img.get("content", "") if og_img else ""

        # Extract follower/stats counts
        followers = ""
        for pat in [
            r'(\d[\d,\.]+)\s*[Ff]ollowers',
            r'"followerCount["\s:]+(\d+)',
            r'subscribers["\s:]+(\d[\d,]+)',
        ]:
            m = re.search(pat, body)
            if m:
                followers = m.group(1)
                break

        return {
            "type": "username_hit",
            "source": name,
            "platform": name,
            "title": title[:200] or f"{name}: {username}",
            "snippet": desc[:400],
            "link": url,
            "url": url,
            "avatar": avatar,
            "thumbnail": avatar,
            "followers": followers,
            "confidence": "HIGH",
            "username": username,
        }
    except Exception as e:
        logging.debug(f"check {name}: {e}")
        return None


def _enrich_platform_info(result: dict) -> dict:
    """Enrich a username_hit result with additional info."""
    try:
        r = safe_get(result.get("url", result.get("link", "")), timeout=10)
        if not r or r.status_code != 200:
            return result
        body = r.text
        platform = result.get("platform", "")

        if platform in ("Twitter/X",):
            for pat, key in [
                (r'(\d[\d,\.]+)\s*[Ff]ollowers', "followers"),
                (r'(\d[\d,\.]+)\s*[Ff]ollowing', "following"),
                (r'(\d[\d,\.]+)\s*[Pp]osts', "posts"),
            ]:
                m = re.search(pat, body)
                if m:
                    result[key] = m.group(1)

        elif platform == "Instagram":
            soup = BeautifulSoup(body, "html.parser")
            for pat, key in [
                (r'"edge_followed_by":\{"count":(\d+)', "followers"),
                (r'"edge_follow":\{"count":(\d+)', "following"),
                (r'"edge_owner_to_timeline_media":\{"count":(\d+)', "posts"),
            ]:
                m = re.search(pat, body)
                if m:
                    result[key] = m.group(1)

        elif platform == "TikTok":
            for pat, key in [
                (r'"followerCount":(\d+)', "followers"),
                (r'"followingCount":(\d+)', "following"),
                (r'"videoCount":(\d+)', "posts"),
            ]:
                m = re.search(pat, body)
                if m:
                    result[key] = m.group(1)

        elif platform == "Telegram":
            m = re.search(r'<div class="tgme_page_title"[^>]*><span[^>]*>([^<]+)', body)
            if m:
                result["name"] = m.group(1).strip()
            desc_m = re.search(r'<div class="tgme_page_description"[^>]*>(.*?)</div>', body, re.DOTALL)
            if desc_m:
                result["bio"] = re.sub(r"<[^>]+>", "", desc_m.group(1)).strip()[:400]
            img_m = re.search(r'<img class="tgme_page_photo_image"[^>]+src="([^"]+)"', body)
            if img_m:
                result["avatar"] = img_m.group(1)
                result["thumbnail"] = img_m.group(1)
            subs_m = re.search(r'(\d[\d,\.]+)\s*(members|subscribers|online)', body, re.IGNORECASE)
            if subs_m:
                result["followers"] = subs_m.group(1)
    except Exception as e:
        logging.debug(f"enrich: {e}")
    return result


def username_scan(username: str, on_event) -> List[dict]:
    """Full username OSINT across 120+ platforms."""
    on_event("log", f"👤 Username scan: {username}")
    results = []
    seen_urls: Set[str] = set()

    # Platform checks in parallel
    def check_and_report(name, url_tpl, patterns):
        r = _check_platform(name, url_tpl, patterns, username)
        return r

    on_event("log", f"  🔍 Checking {len(SOCIAL_PLATFORMS)} platforms...")
    with ThreadPoolExecutor(max_workers=25) as ex:
        futs = {ex.submit(check_and_report, n, u, p): n for n, u, p in SOCIAL_PLATFORMS}
        for f in as_completed(futs):
            name = futs[f]
            try:
                r = f.result()
                if r:
                    url = r.get("url", "")
                    if url not in seen_urls:
                        seen_urls.add(url)
                        results.append(r)
                        on_event("result", r)
                        on_event("log", f"  ✅ FOUND: {name}")
            except Exception as e:
                logging.debug(f"platform fut {name}: {e}")

    # Search engine dorks for username
    on_event("log", "  🔍 Search engine dorks...")
    seen_links: Set[str] = set()
    dorks = [
        f'"{username}" site:instagram.com OR site:twitter.com OR site:facebook.com',
        f'@{username} profile social',
        f'"{username}" username account',
    ]
    for dork in dorks[:3]:
        for fn in [DDG.text, Bing.search]:
            for r in fn(dork, 10):
                link = normalize_url(r.get("link", ""))
                if link and link not in seen_links and link not in seen_urls:
                    # Apply strict username relevance
                    if is_relevant_username(r, username):
                        seen_links.add(link)
                        r["type"] = "username_web"
                        r["source"] = r.get("source", "Web")
                        results.append(r)
                        on_event("result", r)
        time.sleep(0.2)

    # Nitter for Twitter
    on_event("log", "  🐦 Checking Twitter/Nitter...")
    for r in fetch_nitter_profile(username):
        results.append(r)
        on_event("result", r)

    # Image search for username
    for img in DDG.images(f'"{username}" profile', 15):
        img["type"] = "username_image"
        results.append(img)
        on_event("result", img)

    # Final strict filter pass (idempotent)
    try:
        results = apply_relevance_filter(results, username, "username")
    except Exception:
        pass
    on_event("log", f"👤 Username scan done — {len(results)} hits")
    return results


# ═══════════════════════════════════════════════════════════════════════
#  EMAIL RECON
# ═══════════════════════════════════════════════════════════════════════
def email_recon(email: str, on_event) -> dict:
    on_event("log", f"📧 Email OSINT: {email}")
    results = {"providers": [], "leaks": [], "mentions": []}
    email_lower = email.strip().lower()
    local, _, domain = email_lower.partition("@")

    def _add(lst_key, r):
        if is_relevant_email(r, email_lower):
            results[lst_key].append(r)
            on_event("result", r)

    # Provider info
    try:
        r = safe_get(f"https://{domain}", timeout=8)
        if r and r.status_code == 200:
            soup = BeautifulSoup(r.text, "html.parser")
            og_title = soup.find("meta", property="og:title")
            title = og_title.get("content", domain) if og_title else domain
            results["providers"].append({
                "type": "email_provider",
                "source": "Domain Check",
                "title": f"Provider: {title}",
                "link": f"https://{domain}",
                "snippet": f"Email domain {domain} resolves successfully",
            })
            on_event("result", results["providers"][-1])
    except Exception:
        pass

    # Have I Been Pwned (public check)
    try:
        r = safe_get(
            f"https://haveibeenpwned.com/api/v3/breachedaccount/{quote_plus(email_lower)}",
            headers={"hibp-api-key": "demo", "user-agent": "ALKASER-NEXUS-OSINT"},
            timeout=10,
        )
        if r and r.status_code == 200:
            for breach in (r.json() or [])[:10]:
                item = {
                    "type": "email_leak",
                    "source": "HaveIBeenPwned",
                    "title": f"BREACH: {breach.get('Name', '')}",
                    "link": f"https://haveibeenpwned.com/account/{quote_plus(email_lower)}",
                    "snippet": f"Compromised: {breach.get('BreachDate', '')} — Data: {', '.join(breach.get('DataClasses', [])[:5])}",
                    "breach_date": breach.get("BreachDate", ""),
                }
                _add("leaks", item)
    except Exception as e:
        logging.debug(f"hibp: {e}")

    # Search engine dorks
    on_event("log", "  🔍 Searching email mentions...")
    seen = set()
    for dork in DorkEngine.dorks_for_email(email_lower):
        for fn in [DDG.text, Bing.search]:
            for r in fn(dork, 10):
                link = normalize_url(r.get("link", ""))
                if link and link not in seen:
                    if is_relevant_email(r, email_lower):
                        seen.add(link)
                        r["type"] = "email_mention"
                        r["source"] = r.get("source", "Web")
                        _add("mentions", r)
        time.sleep(0.2)

    # Social search for email
    on_event("log", "  🔍 Social platform search...")
    for fn in [DDG.text, Bing.search]:
        for r in fn(f'"{email_lower}" profile OR account OR social', 10):
            link = normalize_url(r.get("link", ""))
            if link and link not in seen:
                if is_relevant_email(r, email_lower):
                    seen.add(link)
                    r["type"] = "email_social"
                    _add("mentions", r)

    on_event("log", f"📧 Email recon done — {sum(len(v) for v in results.values())} items")
    return results


# ═══════════════════════════════════════════════════════════════════════
#  ADVANCED NAME INTELLIGENCE — Exact Multi-word Name Matching
# ═══════════════════════════════════════════════════════════════════════
class NameIntelligence:
    @staticmethod
    def normalize_name(name: str) -> str:
        name = name.strip()
        name = re.sub(r'[أإآا]', 'ا', name)
        name = re.sub(r'[ةه]$', 'ة', name)
        name = re.sub(r'[يى]', 'ي', name)
        return name

    @staticmethod
    def build_name_variants(name: str) -> List[str]:
        variants = set()
        parts = name.strip().split()
        variants.add(name)
        variants.add(f'"{name}"')
        if len(parts) >= 2:
            variants.add(f'"{parts[0]} {parts[-1]}"')
            for i in range(len(parts) - 1):
                variants.add(f'"{parts[i]} {parts[i+1]}"')
        if len(parts) >= 3:
            variants.add(f'"{" ".join(parts[:3])}"')
            variants.add(f'"{" ".join(parts[-3:])}"')
        if len(parts) >= 4:
            variants.add(f'"{" ".join(parts[:4])}"')
            variants.add(f'"{parts[0]} {parts[2]} {parts[-1]}"')
        return list(variants)

    @staticmethod
    def score_result(result: dict, target_name: str) -> float:
        score = 0.0
        name_lower = _norm_text(target_name)
        name_parts = name_lower.split()
        text = _norm_text(
            (result.get("title") or "") + " " +
            (result.get("snippet") or "") + " " +
            (result.get("name") or "") + " " +
            (result.get("bio") or "")
        )
        if name_lower in text:
            score += 1.0
        if all(p in text for p in name_parts):
            score += 0.7
        parts_found = sum(1 for p in name_parts if p in text and len(p) > 2)
        score += (parts_found / max(len(name_parts), 1)) * 0.5
        return min(score, 2.5)

    @staticmethod
    def full_name_scan(name: str, on_event) -> List[dict]:
        on_event("log", f"👤 Name Intelligence for: {name}")
        results = []
        seen = set()
        variants = NameIntelligence.build_name_variants(name)
        name_parts = name.strip().split()
        on_event("log", f"  📋 Name parts: {len(name_parts)} | Variants: {len(variants)}")

        social_platforms = [
            ("LinkedIn",   f'"{name}" site:linkedin.com'),
            ("Facebook",   f'"{name}" site:facebook.com'),
            ("Twitter/X",  f'"{name}" site:twitter.com OR site:x.com OR site:nitter.net'),
            ("Instagram",  f'"{name}" site:instagram.com'),
            ("TikTok",     f'"{name}" site:tiktok.com'),
            ("YouTube",    f'"{name}" site:youtube.com'),
            ("Wikipedia",  f'"{name}" site:wikipedia.org OR site:ar.wikipedia.org'),
            ("GitHub",     f'"{name}" site:github.com'),
            ("Reddit",     f'"{name}" site:reddit.com'),
            ("Academia",   f'"{name}" site:academia.edu OR site:researchgate.net OR site:scholar.google.com'),
            ("Documents",  f'"{name}" filetype:pdf OR filetype:doc OR filetype:docx OR filetype:xlsx'),
            ("Directories",f'"{name}" site:truepeoplesearch.com OR site:whitepages.com OR site:spokeo.com OR site:411.com OR site:fastpeoplesearch.com'),
            ("Quora",      f'"{name}" site:quora.com'),
            ("Medium",     f'"{name}" site:medium.com OR site:substack.com'),
            # ── NEWS — العربية الكبرى ──
            ("AlJazeera",  f'"{name}" site:aljazeera.net OR site:aljazeera.com'),
            ("AlArabiya",  f'"{name}" site:alarabiya.net'),
            ("BBC Arabic", f'"{name}" site:bbc.com/arabic OR site:bbc.co.uk'),
            ("CNN Arabic", f'"{name}" site:arabic.cnn.com OR site:cnn.com'),
            ("SkyNewsAr",  f'"{name}" site:skynewsarabia.com'),
            ("RT Arabic",  f'"{name}" site:arabic.rt.com'),
            ("DW Arabic",  f'"{name}" site:dw.com/ar'),
            ("France24",   f'"{name}" site:france24.com/ar OR site:france24.com'),
            ("AlHurra",    f'"{name}" site:alhurra.com'),
            ("Independent",f'"{name}" site:independentarabia.com'),
            ("AlqudsAlAr", f'"{name}" site:alquds.co.uk'),
            ("Asharq",     f'"{name}" site:asharqalawsat.com OR site:aawsat.com'),
            ("Erem News",  f'"{name}" site:eremnews.com'),
            ("AlMayadeen", f'"{name}" site:almayadeen.net'),
            ("AlMasryAlYoum",f'"{name}" site:almasryalyoum.com OR site:youm7.com OR site:masrawy.com'),
            ("Iraq News",  f'"{name}" site:alsumaria.tv OR site:aliraqnews.com OR site:nrttv.com OR site:shafaq.com'),
            ("Saudi News", f'"{name}" site:sabq.org OR site:okaz.com.sa OR site:al-jazirah.com'),
            ("Gulf News",  f'"{name}" site:gulfnews.com OR site:khaleejtimes.com OR site:thenationalnews.com'),
            # ── NEWS — Global wires ──
            ("Reuters",    f'"{name}" site:reuters.com'),
            ("AP",         f'"{name}" site:apnews.com'),
            ("AFP",        f'"{name}" site:afp.com'),
            ("BBC",        f'"{name}" site:bbc.com OR site:bbc.co.uk'),
            ("NYT/WSJ",    f'"{name}" site:nytimes.com OR site:wsj.com OR site:washingtonpost.com'),
            ("Guardian",   f'"{name}" site:theguardian.com OR site:telegraph.co.uk'),
            ("Bloomberg",  f'"{name}" site:bloomberg.com OR site:ft.com OR site:cnbc.com'),
            ("Forbes",     f'"{name}" site:forbes.com OR site:fortune.com OR site:businessinsider.com'),
            ("DailyMail",  f'"{name}" site:dailymail.co.uk OR site:thesun.co.uk OR site:mirror.co.uk'),
            ("Politico",   f'"{name}" site:politico.com OR site:axios.com OR site:thehill.com'),
            ("Yahoo News", f'"{name}" site:news.yahoo.com OR site:msn.com'),
            ("Court/Gov",  f'"{name}" site:gov OR site:courtlistener.com OR site:justice.gov'),
        ]

        def search_platform(platform, dork):
            r_list = []
            try:
                for engine_fn in [DDG.text, Bing.search, Brave.search]:
                    for r in engine_fn(dork, 25):
                        link = normalize_url(r.get("link", ""))
                        if link and link not in seen:
                            # Apply strict name filter
                            if is_relevant_name(r, name):
                                score = NameIntelligence.score_result(r, name)
                                r["platform"] = platform
                                r["relevance_score"] = score
                                r["type"] = "name_hit"
                                r_list.append(r)
                    time.sleep(0.1)
            except Exception as e:
                logging.debug(f"name platform {platform}: {e}")
            return r_list

        with ThreadPoolExecutor(max_workers=12) as ex:
            futs = {ex.submit(search_platform, p, d): p for p, d in social_platforms}
            for f in as_completed(futs):
                platform = futs[f]
                try:
                    hits = f.result() or []
                    for h in hits:
                        link = normalize_url(h.get("link", ""))
                        if link and link not in seen:
                            seen.add(link)
                            results.append(h)
                            on_event("result", h)
                    if hits:
                        on_event("log", f"  ✅ {platform}: {len(hits)} matches")
                except Exception as e:
                    logging.debug(f"name scan {platform}: {e}")

        on_event("log", "  🔍 Deep variant search (all variants)...")
        for variant in variants:
            for engine_fn in [DDG.text, Bing.search, Brave.search]:
                try:
                    for r in engine_fn(variant, 25):
                        link = normalize_url(r.get("link", ""))
                        if link and link not in seen:
                            if is_relevant_name(r, name):
                                seen.add(link)
                                score = NameIntelligence.score_result(r, name)
                                r["relevance_score"] = score
                                r["type"] = "name_hit"
                                results.append(r)
                                on_event("result", r)
                except Exception:
                    pass
            time.sleep(0.15)

        on_event("log", "  🖼️ Image search for name...")
        for img in DDG.images(f'"{name}"', 40):
            img["type"] = "name_image"
            results.append(img)
            on_event("result", img)

        results.sort(key=lambda x: x.get("relevance_score", 0), reverse=True)
        on_event("log", f"👤 Name scan done — {len(results)} results (sorted by relevance)")
        return results


# ═══════════════════════════════════════════════════════════════════════
#  PHONE OSINT — ENHANCED with Owner Identification
# ═══════════════════════════════════════════════════════════════════════
class PhoneOSINT:
    COUNTRY_CODES = {
        "1": "US/CA", "7": "RU/KZ", "20": "EG", "27": "ZA", "30": "GR", "31": "NL",
        "32": "BE", "33": "FR", "34": "ES", "36": "HU", "39": "IT", "40": "RO",
        "41": "CH", "43": "AT", "44": "GB", "45": "DK", "46": "SE", "47": "NO",
        "48": "PL", "49": "DE", "51": "PE", "52": "MX", "53": "CU", "54": "AR",
        "55": "BR", "56": "CL", "57": "CO", "58": "VE", "60": "MY", "61": "AU",
        "62": "ID", "63": "PH", "64": "NZ", "65": "SG", "66": "TH", "81": "JP",
        "82": "KR", "84": "VN", "86": "CN", "90": "TR", "91": "IN", "92": "PK",
        "93": "AF", "94": "LK", "95": "MM", "98": "IR", "212": "MA", "213": "DZ",
        "216": "TN", "218": "LY", "220": "GM", "221": "SN", "225": "CI", "234": "NG",
        "249": "SD", "251": "ET", "254": "KE", "255": "TZ", "256": "UG", "260": "ZM",
        "261": "MG", "263": "ZW", "352": "LU", "353": "IE", "354": "IS", "355": "AL",
        "356": "MT", "357": "CY", "358": "FI", "359": "BG", "370": "LT", "371": "LV",
        "372": "EE", "373": "MD", "374": "AM", "375": "BY", "380": "UA", "381": "RS",
        "385": "HR", "386": "SI", "387": "BA", "389": "MK", "420": "CZ", "421": "SK",
        "423": "LI", "502": "GT", "503": "SV", "504": "HN", "505": "NI", "506": "CR",
        "507": "PA", "591": "BO", "592": "GY", "593": "EC", "595": "PY", "597": "SR",
        "598": "UY", "855": "KH", "856": "LA", "880": "BD", "886": "TW", "960": "MV",
        "961": "LB", "962": "JO", "963": "SY", "964": "IQ", "965": "KW", "966": "SA",
        "967": "YE", "968": "OM", "970": "PS", "971": "AE", "972": "IL", "973": "BH",
        "974": "QA", "975": "BT", "976": "MN", "977": "NP", "994": "AZ", "995": "GE",
        "996": "KG", "998": "UZ",
    }

    COUNTRY_NAMES = {
        "US/CA": "United States / Canada", "EG": "Egypt / مصر", "SA": "Saudi Arabia / السعودية",
        "AE": "UAE / الإمارات", "KW": "Kuwait / الكويت", "QA": "Qatar / قطر",
        "BH": "Bahrain / البحرين", "OM": "Oman / عُمان", "JO": "Jordan / الأردن",
        "LB": "Lebanon / لبنان", "SY": "Syria / سوريا", "IQ": "Iraq / العراق",
        "MA": "Morocco / المغرب", "DZ": "Algeria / الجزائر", "TN": "Tunisia / تونس",
        "LY": "Libya / ليبيا", "SD": "Sudan / السودان", "YE": "Yemen / اليمن",
        "PS": "Palestine / فلسطين", "TR": "Turkey / تركيا", "IR": "Iran / إيران",
        "PK": "Pakistan", "IN": "India", "GB": "United Kingdom", "DE": "Germany",
        "FR": "France", "RU": "Russia", "CN": "China", "BR": "Brazil",
    }

    @staticmethod
    def identify_country(digits):
        for length in [3, 2, 1]:
            prefix = digits[:length]
            if prefix in PhoneOSINT.COUNTRY_CODES:
                code = PhoneOSINT.COUNTRY_CODES[prefix]
                full_name = PhoneOSINT.COUNTRY_NAMES.get(code, code)
                return code, full_name, prefix
        return "Unknown", "Unknown", ""

    @staticmethod
    def check_telegram(digits):
        result = {"platform": "Telegram", "registered": False, "name": "", "url": "", "bio": "", "avatar": ""}
        try:
            tme = f"https://t.me/+{digits}"
            r = safe_get(tme, timeout=12)
            if r and r.status_code == 200:
                text = r.text
                if "tgme_page_title" in text:
                    m = re.search(r'<div class="tgme_page_title"[^>]*><span[^>]*>([^<]+)', text)
                    if m:
                        result["registered"] = True
                        result["name"] = m.group(1).strip()
                        result["url"] = tme
                        result["bio"] = "Phone number is registered on Telegram"
                    img_m = re.search(r'<img class="tgme_page_photo_image"[^>]+src="([^"]+)"', text)
                    if img_m:
                        result["avatar"] = img_m.group(1)
                    desc_m = re.search(r'<div class="tgme_page_description"[^>]*>(.*?)</div>', text, re.DOTALL)
                    if desc_m:
                        result["bio"] = re.sub(r"<[^>]+>", "", desc_m.group(1)).strip()[:400]
        except Exception as e:
            logging.debug(f"tg check: {e}")
        return result

    @staticmethod
    def check_whatsapp(digits):
        result = {
            "platform": "WhatsApp",
            "registered": False,
            "url": f"https://wa.me/{digits}",
            "bio": f"WhatsApp deeplink for +{digits}",
        }
        try:
            r = safe_get(f"https://wa.me/{digits}", timeout=12)
            if r and r.status_code == 200:
                text = r.text
                if "open" in text.lower() and "whatsapp" in text.lower():
                    result["registered"] = True
                    result["bio"] = "Number appears to have WhatsApp — deeplink generated"
        except Exception as e:
            logging.debug(f"wa check: {e}")
        return result

    @staticmethod
    def check_instagram_phone(digits):
        out = []
        queries = [
            f'"{digits}" site:instagram.com',
            f'"+{digits}" site:instagram.com',
            f'"{digits}" instagram',
        ]
        for q in queries:
            try:
                for r in DDG.text(q, max_results=6):
                    if "instagram.com" in r.get("link", ""):
                        if is_relevant_phone(r, digits):
                            r["platform"] = "Instagram"
                            r["type"] = "phone_social"
                            out.append(r)
                time.sleep(0.3)
            except Exception:
                pass
        return out[:6]

    @staticmethod
    def check_facebook_phone(digits):
        out = []
        for v in [digits, f"+{digits}"]:
            try:
                r = safe_get(
                    f"https://www.facebook.com/search/people/?q={quote_plus(v)}",
                    timeout=12,
                )
                if r and r.status_code == 200 and "profile" in r.text:
                    out.append({
                        "platform": "Facebook",
                        "type": "phone_social",
                        "source": "Facebook Search",
                        "title": f"Facebook search for {v}",
                        "link": f"https://www.facebook.com/search/people/?q={quote_plus(v)}",
                        "snippet": "Potential Facebook profile found via phone number search",
                    })
            except Exception:
                pass
        try:
            for r in DDG.text(f'"{digits}" site:facebook.com', max_results=5):
                if "facebook.com" in r.get("link", "") and is_relevant_phone(r, digits):
                    r["platform"] = "Facebook"
                    r["type"] = "phone_social"
                    out.append(r)
        except Exception:
            pass
        return out[:5]

    @staticmethod
    def check_viber(digits):
        result = {
            "platform": "Viber",
            "registered": False,
            "url": f"viber://chat?number=%2B{digits}",
            "bio": "",
        }
        try:
            r = safe_get(f"https://chats.viber.com/+{digits}", timeout=10)
            if r and r.status_code == 200 and "viber" in r.text.lower():
                result["registered"] = True
                result["bio"] = "Number may be registered on Viber"
        except Exception:
            pass
        return result

    @staticmethod
    def check_tiktok_phone(digits):
        out = []
        try:
            for r in DDG.text(f'"{digits}" site:tiktok.com', max_results=5):
                if "tiktok.com" in r.get("link", "") and is_relevant_phone(r, digits):
                    r["platform"] = "TikTok"
                    r["type"] = "phone_social"
                    out.append(r)
        except Exception:
            pass
        return out[:3]

    @staticmethod
    def check_snapchat_phone(digits):
        out = []
        try:
            for r in DDG.text(f'"{digits}" site:snapchat.com OR snapchat number', max_results=5):
                if is_relevant_phone(r, digits):
                    r["platform"] = "Snapchat"
                    r["type"] = "phone_social"
                    out.append(r)
        except Exception:
            pass
        return out[:3]

    @staticmethod
    def check_linkedin_phone(digits):
        out = []
        try:
            for r in DDG.text(f'"{digits}" site:linkedin.com', max_results=5):
                if "linkedin.com" in r.get("link", "") and is_relevant_phone(r, digits):
                    r["platform"] = "LinkedIn"
                    r["type"] = "phone_social"
                    out.append(r)
        except Exception:
            pass
        return out[:3]

    @staticmethod
    def numbering_plan_lookup(digits):
        info = {}
        try:
            r = safe_get(
                f"https://api.opencnam.com/v3/phone/{quote_plus('+' + digits)}?account_sid=hobbyist&format=json",
                timeout=8,
            )
            if r and r.status_code == 200:
                j = r.json()
                if j.get("name"):
                    info["owner_name"] = j["name"]
                    info["cnam_source"] = "OpenCNAM"
        except Exception:
            pass
        return info

    @staticmethod
    def search_owner_name(phone, digits, variants):
        out = []
        name_patterns = []
        dorks = [
            f'"{phone}" name',
            f'"{phone}" owner',
            f'"{phone}" caller',
            f'"+{digits}" name',
            f'"{digits}" registered to',
            f'"{phone}" who is',
            f'"{phone}" contact',
            f'site:truepeoplesearch.com "{phone}"',
            f'site:whitepages.com "{phone}"',
            f'site:spokeo.com "{phone}"',
            f'site:411.com "{phone}"',
            f'site:fastpeoplesearch.com "{phone}"',
            f'site:zabasearch.com "{phone}"',
            f'site:beenverified.com "{phone}"',
            f'site:intelius.com "{phone}"',
            f'site:peoplefinders.com "{phone}"',
            f'truecaller "{phone}"',
            f'"{digits}" "أحمد" OR "محمد" OR "خالد" OR "عبد"',
        ]
        seen = set()
        with ThreadPoolExecutor(max_workers=6) as ex:
            futs = [ex.submit(DDG.text, d, 8) for d in dorks[:12]]
            for f in as_completed(futs):
                try:
                    for r in (f.result() or []):
                        link = normalize_url(r.get("link", ""))
                        if link and link not in seen:
                            if is_relevant_phone(r, phone):
                                seen.add(link)
                                r["type"] = "phone_owner"
                                r["source"] = "Owner Search"
                                out.append(r)
                                snip = r.get("snippet", "")
                                for pat in [
                                    r'\b([A-Z][a-z]+(?:\s+[A-Z][a-z]+){1,3})\b',
                                    r'(?:name|caller|owner|registered to)[:\s]+([A-Za-z\s]{5,40})',
                                ]:
                                    m = re.search(pat, snip)
                                    if m:
                                        name_patterns.append(m.group(1).strip())
                except Exception:
                    pass
        for dork in dorks[6:10]:
            for r in Bing.search(dork, 6):
                link = normalize_url(r.get("link", ""))
                if link and link not in seen and is_relevant_phone(r, phone):
                    seen.add(link)
                    r["type"] = "phone_owner"
                    out.append(r)
        return out[:25], list(set(name_patterns))

    @staticmethod
    def check_web_mentions(phone, digits, variants):
        out = []
        seen = set()
        for v in variants[:3]:
            for fn in [DDG.text, Bing.search, Yandex.search]:
                try:
                    for r in fn(f'"{v}"', 10):
                        link = normalize_url(r.get("link", ""))
                        if link and link not in seen and is_relevant_phone(r, phone):
                            seen.add(link)
                            r["type"] = "web_mention"
                            r["source"] = f"Web Mention ({r.get('source', '')})"
                            out.append(r)
                except Exception:
                    pass
                time.sleep(0.1)
        return out[:30]

    @staticmethod
    def full_scan(phone, on_event):
        on_event("log", f"📱 Starting deep phone OSINT for: {phone}")
        results = {
            "phone": phone, "variants": [], "owner_info": {}, "providers": [],
            "mentions": [], "social_accounts": [], "directories": [],
            "carrier": {}, "web_mentions": [],
        }

        digits = re.sub(r"\D", "", phone)
        if len(digits) < 7:
            on_event("log", "  ❌ Phone number too short")
            return results

        e164 = f"+{digits}"
        variants = list({phone, digits, e164, f"00{digits}"})
        if len(digits) >= 10:
            variants.extend([
                f"({digits[:3]}) {digits[3:6]}-{digits[6:]}",
                f"{digits[:3]}-{digits[3:6]}-{digits[6:]}",
                f"{digits[:3]} {digits[3:6]} {digits[6:]}",
                f"+{digits[:3]} {digits[3:6]} {digits[6:]}",
            ])
        results["variants"] = list(set(variants))

        code, country_name, prefix = PhoneOSINT.identify_country(digits)
        on_event("log", f"  🌍 Country: {country_name} (prefix: +{prefix})")
        results["country"] = country_name
        results["country_code"] = code
        results["country_prefix"] = prefix

        info_r = {
            "type": "phone_info",
            "source": "Phone Analysis",
            "platform": "📱 Phone Lookup",
            "title": f"Phone: {e164}",
            "snippet": f"Country: {country_name} | Code: +{prefix} | Variants: {len(variants)}",
            "country": country_name,
            "variants": variants,
            "link": f"https://www.truepeoplesearch.com/results?phoneno={digits}",
        }
        results["providers"].append(info_r)
        on_event("result", info_r)

        # Telegram
        on_event("log", "  🔍 Checking Telegram registration...")
        tg = PhoneOSINT.check_telegram(digits)
        if tg["registered"]:
            on_event("log", f"  ✅ TELEGRAM REGISTERED: {tg['name'] or 'Yes'}")
            r = {
                "type": "phone_social", "source": "Telegram", "platform": "Telegram ✅",
                "title": f"Telegram: {tg.get('name', 'Registered User')}",
                "snippet": tg["bio"] or "Registered on Telegram",
                "link": tg["url"], "url": tg["url"],
                "avatar": tg.get("avatar", ""), "registered": True,
                "confidence": "HIGH",
            }
            results["social_accounts"].append(r)
            on_event("result", r)
        else:
            on_event("log", "  ⚪ Telegram: Not found")

        # WhatsApp
        on_event("log", "  🔍 Checking WhatsApp...")
        wa = PhoneOSINT.check_whatsapp(digits)
        r = {
            "type": "phone_social", "source": "WhatsApp", "platform": "WhatsApp",
            "title": f"WhatsApp: +{digits}",
            "snippet": wa["bio"],
            "link": wa["url"],
            "registered": wa["registered"],
            "confidence": "MEDIUM" if wa["registered"] else "LOW",
        }
        results["social_accounts"].append(r)
        on_event("result", r)

        # Multi-platform checks in parallel
        on_event("log", "  🔍 Checking social platforms...")
        with ThreadPoolExecutor(max_workers=6) as ex:
            futs = {
                ex.submit(PhoneOSINT.check_instagram_phone, digits): "Instagram",
                ex.submit(PhoneOSINT.check_facebook_phone, digits): "Facebook",
                ex.submit(PhoneOSINT.check_tiktok_phone, digits): "TikTok",
                ex.submit(PhoneOSINT.check_snapchat_phone, digits): "Snapchat",
                ex.submit(PhoneOSINT.check_linkedin_phone, digits): "LinkedIn",
                ex.submit(PhoneOSINT.check_viber, digits): "Viber",
            }
            for f in as_completed(futs):
                plat = futs[f]
                try:
                    res = f.result()
                    if isinstance(res, list):
                        for item in res:
                            results["social_accounts"].append(item)
                            on_event("result", item)
                    elif isinstance(res, dict):
                        item = {
                            "type": "phone_social", "source": plat, "platform": plat,
                            "title": f"{plat}: +{digits}",
                            "snippet": res.get("bio", ""),
                            "link": res.get("url", ""),
                            "registered": res.get("registered", False),
                        }
                        results["social_accounts"].append(item)
                        on_event("result", item)
                except Exception:
                    pass

        # Owner name search
        on_event("log", "  🔍 Searching for owner name...")
        owner_results, name_patterns = PhoneOSINT.search_owner_name(phone, digits, variants)
        results["directories"] = owner_results
        for r in owner_results:
            on_event("result", r)
        if name_patterns:
            on_event("log", f"  👤 Possible names: {', '.join(name_patterns[:3])}")
            results["owner_info"]["possible_names"] = name_patterns

        # Web mentions
        on_event("log", "  🔍 Web mention scan...")
        web_mentions = PhoneOSINT.check_web_mentions(phone, digits, variants)
        results["web_mentions"] = web_mentions
        for r in web_mentions:
            on_event("result", r)

        # Leak search
        on_event("log", "  🔥 Leak database search...")
        for r in PasteSites.search(phone)[:10]:
            if is_relevant_phone(r, phone):
                results["mentions"].append(r)
                on_event("result", r)

        on_event("log", f"📱 Phone scan done — {sum(len(v) if isinstance(v, list) else 0 for v in results.values())} items")
        return results


def upload_to_imgbb(image_bytes: bytes):
    """Upload image to imgbb.com – returns direct image URL."""
    if not IMGBB_API_KEY:
        return None
    try:
        import base64
        b64 = base64.b64encode(image_bytes).decode("utf-8")
        r = requests.post(
            "https://api.imgbb.com/1/upload",
            data={"key": IMGBB_API_KEY, "image": b64},
            timeout=15,
        )
        if r.status_code == 200:
            j = r.json()
            if j.get("success"):
                return j["data"]["url"]
    except Exception:
        pass
    return None


# ═══════════════════════════════════════════════════════════════════════
#  REVERSE IMAGE SEARCH — Fully Improved (5 engines)
# ═══════════════════════════════════════════════════════════════════════
def reverse_image_yandex(image_url=None, image_bytes=None, on_event=None) -> List[dict]:
    """
    Yandex reverse image search – creates a fresh, clean Chrome session
    using undetected_chromedriver and a temporary user-data-dir to avoid
    DevToolsActivePort conflicts and captcha.
    """
    out = []
    def _log(m):
        if on_event: on_event("log", m)

    if not image_url:
        _log("  ⚪ Yandex: no image URL provided")
        return out

    try:
        # ---- استيراد undetected_chromedriver ----
        try:
            import undetected_chromedriver as uc
        except ImportError:
            _log("  ❌ Yandex: undetected_chromedriver not installed. Run: pip install undetected-chromedriver")
            return out

        # ---- إنشاء مجلد مؤقت جديد كلياً لكل جلسة ----
        temp_profile_dir = tempfile.mkdtemp(prefix="yandex_profile_")
        _log(f"  📁 Created temporary profile: {temp_profile_dir}")

        # ---- إعداد خيارات Chrome ----
        chrome_options = Options()
        chrome_options.add_argument("--no-sandbox")
        chrome_options.add_argument("--disable-dev-shm-usage")
        chrome_options.add_argument("--disable-gpu")
        chrome_options.add_argument("--window-size=1920,1080")
        chrome_options.add_argument("--lang=en-US")
        chrome_options.add_argument("--user-agent=" + random.choice(USER_AGENTS))
        chrome_options.add_argument("--disable-blink-features=AutomationControlled")
        chrome_options.add_experimental_option("excludeSwitches", ["enable-automation"])
        chrome_options.add_experimental_option("useAutomationExtension", False)
        # توجيه Chrome إلى المجلد المؤقت
        chrome_options.add_argument(f"--user-data-dir={temp_profile_dir}")

        # ---- إنشاء متصفح غير قابل للكشف ----
        driver = uc.Chrome(
            headless=True,
            use_subprocess=False,
            options=chrome_options,
            driver_executable_path=None,  # يبحث تلقائياً
        )
        driver.execute_script("Object.defineProperty(navigator, 'webdriver', {get: () => undefined})")

        search_url = f"https://yandex.com/images/search?rpt=imageview&url={quote_plus(image_url)}"
        _log(f"  🌐 Yandex (clean session): loading...")
        driver.get(search_url)

        # ---- انتظر ظهور النتائج بصبر ----
        try:
            WebDriverWait(driver, 30).until(
                EC.presence_of_element_located(
                    (By.CSS_SELECTOR, ".serp-item, [data-bem], img.serp-item__thumb, .ImagesContentImage-Image")
                )
            )
        except Exception:
            _log("  ⚠️ Yandex: timed out waiting for results")

        page_source = driver.page_source
        driver.quit()

        # ---- تنظيف المجلد المؤقت ----
        import shutil
        shutil.rmtree(temp_profile_dir, ignore_errors=True)

        soup = BeautifulSoup(page_source, "html.parser")

        # ----- 1) استخراج من data-bem -----
        for item in soup.select("[data-bem]"):
            try:
                bem = json.loads(item["data-bem"])
                for key in ["serp-item", "serpItem", "ImgSerpItem"]:
                    si = bem.get(key)
                    if si and isinstance(si, dict):
                        img_href = si.get("img_href") or ""
                        preview = si.get("preview") or []
                        if isinstance(preview, list) and preview:
                            img_href = img_href or preview[0].get("url", "")
                        link = si.get("detail_url") or si.get("dups_url") or ""
                        title = si.get("snippet", {}).get("title", "") or link
                        if link or img_href:
                            out.append({
                                "type": "reverse_image",
                                "source": "Yandex",
                                "title": title[:200] or "Yandex Match",
                                "link": link,
                                "image_url": img_href,
                                "thumbnail": img_href,
                                "snippet": (si.get("snippet") or {}).get("text", "")[:300],
                            })
            except Exception:
                pass

        # ----- 2) روابط خارجية -----
        if not out:
            for a in soup.select("a.serp-item__link[href]"):
                href = a.get("href")
                if href and href.startswith("http") and "yandex" not in href:
                    title = a.get_text(strip=True)[:150]
                    out.append({
                        "type": "reverse_image",
                        "source": "Yandex Pages",
                        "title": title,
                        "link": href,
                        "snippet": "Page with similar image",
                    })

        # ----- 3) صور مباشرة -----
        if not out:
            for img in soup.select("img.serp-item__thumb, img.ImagesContentImage-Image"):
                src = img.get("src") or img.get("data-src", "")
                if src and src.startswith("http"):
                    out.append({
                        "type": "reverse_image",
                        "source": "Yandex Image",
                        "title": "Yandex similar image",
                        "link": src,
                        "image_url": src,
                        "thumbnail": src,
                        "snippet": "Yandex image result",
                    })

        _log(f"  ✅ Yandex reverse: {len(out)} matches")
        return out

    except Exception as e:
        _log(f"  ❌ Yandex error: {str(e)}")
        return out


def reverse_image_bing(image_url=None, image_bytes=None, on_event=None):
    """
    Bing reverse image search using Selenium with direct image URL.
    (No file upload; expects image_url from imgbb.)
    """
    out = []
    def _log(m):
        if on_event: on_event("log", m)

    if not image_url:
        _log("  ⚪ Bing: no image URL provided (skipping)")
        return out

    try:
        chrome_options = Options()
        chrome_options.add_argument("--headless=new")
        chrome_options.add_argument("--no-sandbox")
        chrome_options.add_argument("--disable-dev-shm-usage")
        chrome_options.add_argument("--disable-gpu")
        chrome_options.add_argument("--window-size=1920,1080")
        chrome_options.add_argument("--user-agent=" + random.choice(USER_AGENTS))
        chrome_options.add_argument("--lang=en-US")
        chrome_options.add_argument("--disable-blink-features=AutomationControlled")
        chrome_options.add_experimental_option("excludeSwitches", ["enable-automation"])
        chrome_options.add_experimental_option("useAutomationExtension", False)

        if WDM_AVAILABLE:
            service = Service(ChromeDriverManager().install())
        else:
            service = Service()

        driver = webdriver.Chrome(service=service, options=chrome_options)
        driver.execute_script("Object.defineProperty(navigator, 'webdriver', {get: () => undefined})")

        search_url = f"https://www.bing.com/images/search?view=detailv2&iss=sbi&form=SBIVSP&sbisrc=UrlPaste&q=imgurl:{quote_plus(image_url)}"
        _log(f"  🌐 Bing Selenium: opening {search_url}")
        driver.get(search_url)

        try:
            WebDriverWait(driver, 15).until(
                EC.presence_of_element_located((By.CSS_SELECTOR, "a.iusc, div.iacfmit, img.mimg"))
            )
        except:
            _log("  ⚠️ Timed out, but will try to parse anyway...")

        page_source = driver.page_source
        driver.quit()

        soup = BeautifulSoup(page_source, "html.parser")

        # --- استخراج iusc ---
        for a in soup.select("a.iusc"):
            m_str = a.get("m", "")
            if not m_str:
                continue
            try:
                data = json.loads(m_str)
                purl = data.get("purl", "")
                murl = data.get("murl", "")
                turl = data.get("turl", "")
                title = data.get("t", "") or purl
                desc = data.get("desc", "")
                if purl or murl:
                    full_img = murl or turl
                    out.append({
                        "type": "reverse_image",
                        "source": "Bing",
                        "title": title[:200] or "Bing match",
                        "link": purl if purl else murl,
                        "image_url": full_img,
                        "thumbnail": turl or full_img,
                        "snippet": desc[:300],
                    })
            except:
                pass

        # --- استخراج iacfmit (Visual Match) ---
        for item_div in soup.select("div.iacfmit"):
            a_tag = item_div.select_one("a")
            if not a_tag:
                continue
            m_attr = a_tag.get("m", "")
            img_tag = item_div.select_one("img")
            img_src = img_tag.get("src") if img_tag else ""

            purl = ""
            murl = ""
            title = ""
            if m_attr:
                try:
                    clean_m = m_attr.replace("&quot;", '"')
                    data = json.loads(clean_m)
                    purl = data.get("purl", "")
                    murl = data.get("mediaurl") or data.get("url") or data.get("murl", "")
                    title = data.get("t", "")
                except:
                    pass

            if not purl and not murl and not img_src:
                continue
            link = purl if purl else (murl if murl else img_src)
            display_img = murl if murl else img_src
            out.append({
                "type": "reverse_image",
                "source": "Bing Visual Match",
                "title": title[:150] or (img_tag.get("alt") if img_tag else "Visual match"),
                "link": link,
                "image_url": display_img,
                "thumbnail": img_src or display_img,
                "snippet": "Visual match",
            })

        # إزالة التكرار
        seen = set()
        uniq = []
        for item in out:
            key = item.get("link") or item.get("image_url")
            if key and key not in seen:
                seen.add(key)
                uniq.append(item)

        _log(f"  ✅ Bing: {len(uniq)} results")
        return uniq

    except Exception as e:
        logging.debug(f"bing selenium: {e}")
        return out

def reverse_image_tineye(image_url=None, image_bytes=None, on_event=None) -> List[dict]:
    """
    TinEye reverse image search.
    Reads div.match elements with result count.
    """
    out = []
    def _log(m):
        if on_event: on_event("log", m)

    try:
        r = None
        if image_bytes:
            _log("  🔍 TinEye: uploading image...")
            r = session.post(
                "https://tineye.com/search",
                files={"image": ("image.jpg", image_bytes, "image/jpeg")},
                headers={"User-Agent": random.choice(USER_AGENTS)},
                timeout=30, verify=False,
            )
        elif image_url:
            _log("  🔍 TinEye: URL search...")
            r = safe_get(f"https://tineye.com/search?url={quote_plus(image_url)}", timeout=20)

        if not r:
            _log("  ⚪ TinEye: no response")
            return out

        soup = BeautifulSoup(r.text, "html.parser")

        # Extract result count
        count_el = soup.select_one(".result-count, .matches, h2.h2")
        count_text = count_el.get_text(strip=True) if count_el else ""
        total_m = re.search(r'(\d[\d,]*)\s*(?:result|match)', count_text, re.IGNORECASE)
        total_matches = total_m.group(1) if total_m else "?"
        if total_matches != "?":
            _log(f"  📊 TinEye: {total_matches} total matches found")

        # Read div.match blocks
        for res in soup.select("div.match, div.result-wrap, li.match")[:30]:
            a_url = res.select_one("a.match-url, a.link-url, a[href^='http']")
            img_el = res.select_one("img.match-image, img.result-image, img")
            title_el = res.select_one(".match-title, .result-title, h4, h3")
            domain_el = res.select_one(".match-domain, .domain, .crawl-date")

            link = ""
            if a_url:
                link = a_url.get("href", "")
            img_src = ""
            if img_el:
                img_src = img_el.get("src") or img_el.get("data-src", "")
                if img_src and not img_src.startswith("http"):
                    img_src = ""
            title = ""
            if title_el:
                title = title_el.get_text(strip=True)
            elif link:
                title = urlparse(link).netloc

            crawl_date = ""
            if domain_el:
                crawl_date = domain_el.get_text(strip=True)

            if link or img_src:
                out.append({
                    "type": "reverse_image", "source": "TinEye",
                    "title": title[:200] or link[:100],
                    "link": link,
                    "image_url": img_src,
                    "thumbnail": img_src,
                    "snippet": f"TinEye match — {crawl_date}" + (f" | Total: {total_matches} matches" if total_matches != "?" else ""),
                })

        # If no matches via divs, check for "no results"
        if not out and total_matches == "0":
            _log("  ⚪ TinEye: 0 matches found")
        elif not out:
            # Try alternate selectors
            for a in soup.select("a[href*='tineye.com/matches']")[:10]:
                out.append({
                    "type": "reverse_image", "source": "TinEye",
                    "title": f"TinEye Matches ({total_matches})",
                    "link": urljoin("https://tineye.com", a.get("href", "")),
                    "snippet": f"TinEye found {total_matches} matches",
                })

        _log(f"  ✅ TinEye reverse: {len(out)} results extracted")
        return out

    except Exception as e:
        logging.debug(f"tineye: {e}")
        return out


def reverse_image_google_lens(image_url=None, image_bytes=None, on_event=None) -> List[dict]:
    """
    Google Lens reverse image search.
    Extracts images AND links from page for maximum results.
    """
    out = []
    def _log(m):
        if on_event: on_event("log", m)

    try:
        if not image_url and not image_bytes:
            return out

        r = None
        if image_url:
            _log("  🔍 Google Lens: URL search...")
            r = safe_get(
                f"https://lens.google.com/uploadbyurl?url={quote_plus(image_url)}",
                headers={
                    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36",
                    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8",
                    "Referer": "https://lens.google.com/",
                    "Accept-Language": "en-US,en;q=0.9",
                },
                timeout=20,
            )

        if not r or r.status_code not in (200, 302):
            # Fallback: Google Images search
            if image_url:
                _log("  🔍 Google Lens: fallback to Google Images...")
                r = safe_get(
                    f"https://www.google.com/searchbyimage?image_url={quote_plus(image_url)}&safe=off",
                    headers={"User-Agent": random.choice(USER_AGENTS)},
                    timeout=20,
                )

        if not r:
            _log("  ⚪ Google Lens: no response")
            return out

        soup = BeautifulSoup(r.text, "html.parser")

        # Extract all meaningful links
        seen_links = set()
        for a in soup.select("a[href^='http']")[:50]:
            href = a.get("href", "")
            # Skip Google internal, ads, and useless links
            if not href or "google" in href.lower() or "javascript" in href.lower():
                continue
            if href in seen_links:
                continue
            seen_links.add(href)
            txt = a.get_text(strip=True)[:160] or urlparse(href).netloc
            if len(txt) < 4:
                continue
            out.append({
                "type": "reverse_image", "source": "Google Lens",
                "title": txt,
                "link": href,
                "snippet": "Google Lens related page",
            })

        # Extract images referenced in the page
        for img in soup.select("img[src^='http'], img[data-src^='http']")[:20]:
            src = img.get("src") or img.get("data-src", "")
            if src and "google" not in src:
                alt = img.get("alt", "")
                out.append({
                    "type": "reverse_image", "source": "Google Lens Image",
                    "title": alt[:150] or "Lens image result",
                    "link": src, "image_url": src, "thumbnail": src,
                    "snippet": "Image found by Google Lens",
                })

        # Try extracting JSON data
        for script in soup.select("script"):
            txt = script.get_text()
            if "AF_initDataCallback" in txt or "window.google" in txt:
                urls = re.findall(r'https?://[^\s"\'\\]{20,200}', txt)
                for u in urls[:10]:
                    if "google" not in u and u not in seen_links:
                        seen_links.add(u)
                        out.append({
                            "type": "reverse_image", "source": "Google Lens JSON",
                            "title": urlparse(u).netloc,
                            "link": u,
                            "snippet": "Found via Google Lens data",
                        })

        _log(f"  ✅ Google Lens: {len(out)} results")
        return out[:40]

    except Exception as e:
        logging.debug(f"glens: {e}")
        return out


def reverse_image_saucenao(image_url=None, image_bytes=None, on_event=None) -> List[dict]:
    """
    SauceNAO reverse image search — best for anime/art/manga.
    Filters results with similarity >= 45%.
    """
    out = []
    def _log(m):
        if on_event: on_event("log", m)

    try:
        r = None
        if image_bytes:
            _log("  🔍 SauceNAO: uploading image...")
            r = session.post(
                "https://saucenao.com/search.php?db=999&output_type=2&numres=16",
                files={"file": ("image.jpg", image_bytes, "image/jpeg")},
                headers={
                    "User-Agent": random.choice(USER_AGENTS),
                    "Accept": "application/json",
                },
                timeout=30, verify=False,
            )
        elif image_url:
            _log("  🔍 SauceNAO: URL search...")
            r = safe_get(
                f"https://saucenao.com/search.php?url={quote_plus(image_url)}&db=999&output_type=2&numres=16",
                headers={"Accept": "application/json"},
                timeout=20,
            )

        if not r:
            _log("  ⚪ SauceNAO: no response")
            return out

        try:
            j = r.json()
        except Exception:
            _log("  ⚪ SauceNAO: invalid JSON response")
            return out

        results_list = j.get("results") or []
        for result in results_list:
            header = result.get("header") or {}
            data = result.get("data") or {}
            similarity = float(header.get("similarity", 0))
            # Filter: only show results with >= 45% similarity
            if similarity < 45.0:
                continue

            ext_urls = data.get("ext_urls") or []
            title = (
                data.get("title") or
                data.get("source") or
                data.get("material") or
                header.get("index_name", "")
            )
            creator = data.get("creator") or data.get("author_name") or ""
            chars = ", ".join(data.get("characters", [])[:3]) if data.get("characters") else ""

            snippet_parts = [f"Similarity: {similarity:.1f}%"]
            if creator:
                snippet_parts.append(f"Creator: {creator}")
            if chars:
                snippet_parts.append(f"Characters: {chars}")

            out.append({
                "type": "reverse_image", "source": "SauceNAO",
                "title": (title or "SauceNAO Match")[:200],
                "link": ext_urls[0] if ext_urls else "",
                "thumbnail": header.get("thumbnail", ""),
                "image_url": header.get("thumbnail", ""),
                "snippet": " | ".join(snippet_parts),
                "similarity": similarity,
                "confidence": "HIGH" if similarity >= 80 else ("MEDIUM" if similarity >= 60 else "LOW"),
            })

        # Sort by similarity descending
        out.sort(key=lambda x: x.get("similarity", 0), reverse=True)
        _log(f"  ✅ SauceNAO: {len(out)} results (≥45% similarity)")
        return out

    except Exception as e:
        logging.debug(f"saucenao: {e}")
        return out


def reverse_image_all(image_url: Optional[str], image_bytes: Optional[bytes], on_event) -> List[dict]:
    """Run all 5 reverse image engines in parallel."""
    on_event("log", "🖼️ Starting reverse image search (5 engines)...")
    all_results = []
    seen = set()

    engines = [
        ("Yandex",       reverse_image_yandex),
        ("Bing",         reverse_image_bing),
        ("TinEye",       reverse_image_tineye),
        ("Google Lens",  reverse_image_google_lens),
        ("SauceNAO",     reverse_image_saucenao),
    ]

    def run_engine(name, fn):
        try:
            return name, fn(image_url=image_url, image_bytes=image_bytes, on_event=on_event)
        except Exception as e:
            logging.debug(f"reverse_img {name}: {e}")
            return name, []

    with ThreadPoolExecutor(max_workers=5) as ex:
        futs = {ex.submit(run_engine, n, fn): n for n, fn in engines}
        for f in as_completed(futs):
            name = futs[f]
            try:
                _, results = f.result()
                for r in (results or []):
                    key = r.get("link") or r.get("image_url") or r.get("title")
                    if key and key not in seen:
                        seen.add(key)
                        all_results.append(r)
                        on_event("result", r)
            except Exception as e:
                logging.debug(f"reverse_img fut {name}: {e}")

    on_event("log", f"🖼️ Reverse image done — {len(all_results)} total results")
    return all_results


# ═══════════════════════════════════════════════════════════════════════
#  SOCIAL SCRAPERS
# ═══════════════════════════════════════════════════════════════════════
def fetch_reddit_posts(username, limit=10):
    posts = []
    try:
        r = safe_get(
            f"https://www.reddit.com/user/{username}/submitted.json?limit={limit}",
            headers={"User-Agent": random.choice(USER_AGENTS)},
            timeout=12,
        )
        if not r or r.status_code != 200:
            return posts
        for child in r.json().get("data", {}).get("children", []):
            d = child.get("data", {})
            thumb = d.get("thumbnail", "") if (d.get("thumbnail", "") or "").startswith("http") else ""
            media = d.get("url_overridden_by_dest") or d.get("url", "")
            ptype = "video" if d.get("is_video") else (
                "image" if media and any(media.endswith(x) for x in [".jpg", ".png", ".gif", ".jpeg"]) else "text"
            )
            posts.append({
                "type": "reddit_post", "platform": "Reddit",
                "source": f"Reddit r/{d.get('subreddit', 'unknown')}",
                "title": d.get("title", ""),
                "snippet": (d.get("selftext") or "")[:400],
                "link": f"https://reddit.com{d.get('permalink', '')}",
                "url": f"https://reddit.com{d.get('permalink', '')}",
                "thumbnail": thumb, "image_url": media if ptype == "image" else "",
                "score": d.get("score", 0), "comments": d.get("num_comments", 0),
                "subreddit": d.get("subreddit", ""),
                "author": d.get("author", username),
                "created_utc": d.get("created_utc", ""),
            })
    except Exception as e:
        logging.debug(f"Reddit posts: {e}")
    return posts


def fetch_reddit_comments(username, limit=10):
    out = []
    try:
        r = safe_get(
            f"https://www.reddit.com/user/{username}/comments.json?limit={limit}",
            headers={"User-Agent": random.choice(USER_AGENTS)},
            timeout=12,
        )
        if not r or r.status_code != 200:
            return out
        for child in r.json().get("data", {}).get("children", []):
            d = child.get("data", {})
            out.append({
                "type": "reddit_comment", "platform": "Reddit",
                "source": f"Reddit r/{d.get('subreddit', 'unknown')}",
                "title": f"Comment in: {d.get('link_title', '')[:100]}",
                "snippet": (d.get("body") or "")[:400],
                "link": f"https://reddit.com{d.get('permalink', '')}",
                "score": d.get("score", 0), "subreddit": d.get("subreddit", ""),
                "author": d.get("author", username),
            })
    except Exception as e:
        logging.debug(f"Reddit comments: {e}")
    return out


def fetch_github_repos(username):
    out = []
    try:
        r = safe_get(
            f"https://api.github.com/users/{username}/repos?sort=updated&per_page=20",
            headers={"Accept": "application/vnd.github+json"},
            timeout=12,
        )
        if not r or r.status_code != 200:
            return out
        for repo in r.json():
            out.append({
                "type": "github_repo", "platform": "GitHub", "source": "GitHub Repository",
                "title": repo.get("full_name", ""),
                "snippet": (repo.get("description") or "")[:400],
                "link": repo.get("html_url", ""),
                "language": repo.get("language", ""),
                "stars": repo.get("stargazers_count", 0),
                "forks": repo.get("forks_count", 0),
                "topics": repo.get("topics", []),
                "updated_at": repo.get("updated_at", ""),
                "is_fork": repo.get("fork", False),
            })
    except Exception as e:
        logging.debug(f"GitHub repos: {e}")
    return out


def fetch_github_code_mentions(target, max_results=10):
    out = []
    try:
        r = safe_get(
            f"https://api.github.com/search/code?q={quote_plus(target)}",
            headers={"Accept": "application/vnd.github+json"},
        )
        if not r or r.status_code != 200:
            return out
        for it in (r.json().get("items") or [])[:max_results]:
            out.append({
                "type": "github_code", "source": "GitHub Code",
                "title": it.get("name", ""),
                "snippet": f"repo: {it.get('repository', {}).get('full_name', '')}",
                "link": it.get("html_url", ""),
            })
    except Exception as e:
        logging.debug(f"github code: {e}")
    return out


def fetch_youtube_videos(query, max_results=6):
    videos = []
    try:
        r = safe_get(f"https://www.youtube.com/results?search_query={quote_plus(query)}", timeout=12)
        if not r or r.status_code != 200:
            return videos
        text = r.text
        vids = list(set(re.findall(r'/watch\?v=([a-zA-Z0-9_-]{11})', text)))[:max_results]
        for v in vids:
            videos.append({
                "type": "youtube_video", "platform": "YouTube", "source": "YouTube Video",
                "title": f"YouTube video {v}",
                "link": f"https://www.youtube.com/watch?v={v}",
                "thumbnail": f"https://img.youtube.com/vi/{v}/hqdefault.jpg",
                "image_url": f"https://img.youtube.com/vi/{v}/hqdefault.jpg",
                "video_id": v,
            })
    except Exception as e:
        logging.debug(f"yt videos: {e}")
    return videos


def fetch_youtube_channels(query, max_results=5):
    chans = []
    try:
        r = safe_get(
            f"https://www.youtube.com/results?search_query={quote_plus(query)}&sp=EgIQAg%253D%253D",
            timeout=12,
        )
        if not r or r.status_code != 200:
            return chans
        chan_ids = list(set(re.findall(r'"channelId":"([^"]+)"', r.text)))[:max_results]
        for cid in chan_ids:
            chans.append({
                "type": "youtube_channel", "platform": "YouTube", "source": "YouTube Channel",
                "title": f"YouTube Channel: {cid}",
                "link": f"https://www.youtube.com/channel/{cid}",
                "thumbnail": "", "channel_id": cid,
            })
    except Exception as e:
        logging.debug(f"yt chans: {e}")
    return chans


def fetch_instagram_posts_dork(username, max_results=10):
    posts = []
    seen = set()
    dorks = [
        f'"{username}" site:instagram.com',
        f'"@{username}" site:instagram.com',
        f'instagram.com/{username}',
    ]
    for dork in dorks[:3]:
        try:
            for r in DDG.text(dork, max_results=8):
                link = r.get("href", "") or r.get("link", "")
                if link and link not in seen:
                    if is_relevant_username(r, username):
                        seen.add(link)
                        posts.append({
                            "type": "instagram_post", "platform": "Instagram", "source": "Instagram (Public)",
                            "title": r.get("title", "")[:150], "snippet": r.get("body", "")[:300],
                            "link": link, "url": link, "suggested_username": username,
                        })
            time.sleep(0.2)
        except Exception as e:
            logging.debug(f"ig dork: {e}")
    return posts[:max_results]


def fetch_nitter_profile(username):
    out = []
    instances = [
        "https://nitter.net",
        "https://nitter.privacydev.net",
        "https://nitter.poast.org",
        "https://nitter.1d4.us",
        "https://nitter.unixfox.eu",
    ]
    for base in instances:
        try:
            r = safe_get(f"{base}/{username}", timeout=10)
            if not r or r.status_code != 200:
                continue
            soup = BeautifulSoup(r.text, "html.parser")
            profile = soup.select_one("div.profile-card")
            if profile:
                name = profile.select_one(".profile-card-fullname")
                bio = profile.select_one(".profile-bio p")
                followers = profile.select_one(".followers .profile-stat-num")
                following = profile.select_one(".following .profile-stat-num")
                tweets_count = profile.select_one(".tweets .profile-stat-num")
                avatar = profile.select_one(".profile-card-avatar img")
                out.append({
                    "type": "twitter_profile", "platform": "Twitter/X (Nitter)", "source": "Nitter Mirror",
                    "title": f"@{username}" + (f" — {name.get_text(strip=True)}" if name else ""),
                    "snippet": (bio.get_text(strip=True) if bio else "")[:400],
                    "link": f"https://x.com/{username}",
                    "followers": (followers.get_text(strip=True) if followers else ""),
                    "following": (following.get_text(strip=True) if following else ""),
                    "posts": (tweets_count.get_text(strip=True) if tweets_count else ""),
                    "avatar": (urljoin(base, avatar.get("src", "")) if avatar else ""),
                })
                for tweet in soup.select("div.timeline-item")[:10]:
                    content = tweet.select_one(".tweet-content")
                    date = tweet.select_one(".tweet-date a")
                    if content:
                        href = date.get("href", "") if date else ""
                        if href and not href.startswith("http"):
                            href = urljoin(base, href)
                        out.append({
                            "type": "twitter_post", "platform": "Twitter/X", "source": "Nitter Mirror",
                            "title": f"Tweet by @{username}",
                            "snippet": content.get_text(strip=True)[:400],
                            "link": href, "url": href,
                        })
                break
        except Exception as e:
            logging.debug(f"nitter {base}: {e}")
    return out


def fetch_facebook_public(username):
    out = []
    try:
        r = safe_get(
            f"https://www.facebook.com/{username}",
            timeout=12,
            headers={"User-Agent": "Mozilla/5.0 (compatible; facebookexternalhit/1.1)"},
        )
        if r and r.status_code == 200:
            soup = BeautifulSoup(r.text, "html.parser")
            og_title = soup.find("meta", property="og:title")
            og_desc = soup.find("meta", property="og:description")
            og_img = soup.find("meta", property="og:image")
            title = og_title.get("content", "") if og_title else ""
            desc = og_desc.get("content", "") if og_desc else ""
            img = og_img.get("content", "") if og_img else ""
            if title and "Facebook" in r.text:
                out.append({
                    "type": "facebook_profile", "platform": "Facebook", "source": "Facebook Public",
                    "title": title, "snippet": desc[:400],
                    "link": f"https://www.facebook.com/{username}",
                    "url": f"https://www.facebook.com/{username}",
                    "avatar": img, "thumbnail": img,
                })
    except Exception as e:
        logging.debug(f"fb public: {e}")
    return out


def fetch_social_mentions(username, max_results=10):
    mentions = []
    seen = set()
    searches = [
        ("Instagram", f'"{username}" site:instagram.com'),
        ("Twitter/X", f'"{username}" site:twitter.com OR site:x.com'),
        ("TikTok", f'"{username}" site:tiktok.com'),
        ("Facebook", f'"{username}" site:facebook.com'),
        ("LinkedIn", f'"{username}" site:linkedin.com'),
        ("Pinterest", f'"{username}" site:pinterest.com'),
        ("Threads", f'"{username}" site:threads.net'),
        ("YouTube", f'"{username}" site:youtube.com'),
        ("Snapchat", f'"{username}" site:snapchat.com'),
    ]
    for name, dork in searches:
        try:
            for r in DDG.text(dork, max_results=5):
                link = r.get("href", "") or r.get("link", "")
                if link not in seen and is_relevant_username(r, username):
                    seen.add(link)
                    mentions.append({
                        "type": "social_mention", "platform": name,
                        "source": f"{name} Mention",
                        "title": r.get("title", "")[:150],
                        "snippet": r.get("body", "")[:300],
                        "link": link, "url": link,
                    })
            time.sleep(0.15)
        except Exception as e:
            logging.debug(f"mention {name}: {e}")
    return mentions[:max_results]


# ═══════════════════════════════════════════════════════════════════════
#  DOMAIN RECON
# ═══════════════════════════════════════════════════════════════════════
def domain_recon(domain: str, on_event) -> List[dict]:
    on_event("log", f"🌐 Domain recon: {domain}")
    out = []
    clean = domain.strip().lower().replace("https://", "").replace("http://", "").rstrip("/")

    # WHOIS
    on_event("log", "  📋 WHOIS lookup...")
    try:
        r = safe_get(f"https://www.whois.com/whois/{clean}", timeout=12)
        if r and r.status_code == 200:
            soup = BeautifulSoup(r.text, "html.parser")
            pre = soup.select_one("pre, .whois-record")
            text = pre.get_text()[:1500] if pre else ""
            if text:
                rec = {
                    "type": "whois", "source": "WHOIS",
                    "title": f"WHOIS: {clean}",
                    "snippet": text[:500],
                    "link": f"https://www.whois.com/whois/{clean}",
                }
                out.append(rec)
                on_event("result", rec)
    except Exception:
        pass

    # DNS lookup
    on_event("log", "  🔍 DNS lookup...")
    try:
        ip = socket.gethostbyname(clean)
        rec = {
            "type": "dns", "source": "DNS Lookup",
            "title": f"DNS: {clean} → {ip}",
            "snippet": f"IP Address: {ip}",
            "link": f"https://who.is/whois-ip/ip-address/{ip}",
        }
        out.append(rec)
        on_event("result", rec)

        # Reverse IP
        try:
            hostname = socket.gethostbyaddr(ip)[0]
            rec2 = {
                "type": "reverse_dns", "source": "Reverse DNS",
                "title": f"Reverse DNS: {ip} → {hostname}",
                "snippet": f"Hostname: {hostname}",
                "link": "",
            }
            out.append(rec2)
            on_event("result", rec2)
        except Exception:
            pass
    except Exception:
        pass

    # SSL cert info
    on_event("log", "  🔒 SSL certificate info...")
    try:
        r = safe_get(f"https://crt.sh/?q={clean}&output=json", headers={"Accept": "application/json"}, timeout=10)
        if r and r.status_code == 200:
            certs = r.json()[:5]
            for cert in certs:
                rec = {
                    "type": "ssl_cert", "source": "crt.sh",
                    "title": f"SSL Cert: {cert.get('common_name', '')}",
                    "snippet": f"Issuer: {cert.get('issuer_name', '')} | Not Before: {cert.get('not_before', '')}",
                    "link": f"https://crt.sh/?id={cert.get('id', '')}",
                }
                out.append(rec)
                on_event("result", rec)
    except Exception:
        pass

    # Subdomains via DDG and crt.sh
    on_event("log", "  🔍 Subdomain discovery...")
    try:
        for r in DDG.text(f"site:{clean} -www", 20):
            parsed = urlparse(r.get("link", ""))
            if parsed.netloc and parsed.netloc != clean:
                rec = {
                    "type": "subdomain", "source": "Subdomain",
                    "title": f"Subdomain: {parsed.netloc}",
                    "snippet": r.get("snippet", "")[:300],
                    "link": r.get("link", ""),
                }
                out.append(rec)
                on_event("result", rec)
    except Exception:
        pass

    # Wayback Machine
    on_event("log", "  📚 Wayback Machine...")
    for r in WaybackMachine.search_text(clean, max_results=10):
        out.append(r)
        on_event("result", r)

    # Dorks
    on_event("log", "  🔍 Dorking...")
    seen = set()
    for dork in DorkEngine.dorks_for_domain(clean)[:6]:
        for r in DorkEngine.run(dork, per_engine=10):
            link = normalize_url(r.get("link", ""))
            if not link or link in seen:
                continue
            seen.add(link)
            r["type"] = "domain_mention"
            out.append(r)
            on_event("result", r)
        time.sleep(0.25)

    # Bing
    try:
        for rec in Bing.search(f"site:{clean}", max_results=20):
            rec["type"] = "domain_page"
            out.append(rec)
            on_event("result", rec)
    except Exception:
        pass

    on_event("log", f"🌐 Domain recon done — {len(out)} artefacts")
    return out


# ═══════════════════════════════════════════════════════════════════════
#  DEEP SEARCH
# ═══════════════════════════════════════════════════════════════════════
class DeepSearch:
    def __init__(self, on_event, exact_match=True, target="",
                 use_archive=True, archive_only=False, deep_files=False,
                 use_tor=False):
        self.on_event = on_event
        self.exact = exact_match
        self.target = target.strip().lower()
        self.use_archive = use_archive
        self.archive_only = archive_only
        self.deep_files = deep_files
        self.use_tor = use_tor
        self.results = []
        self.seen = set()
        self._lock = threading.Lock()

    def _is_match(self, r):
        # Archive items: keep all — they are already URL-scoped to the target
        if str(r.get("type", "")).startswith("archive"):
            return True
        if not self.exact or not self.target:
            return True
        return is_relevant_generic(r, self.target)

    def add(self, r):
        if not self._is_match(r):
            return False
        link = r.get("link") or r.get("image_url") or r.get("title", "")
        key = normalize_url(link)
        with self._lock:
            if not key or key in self.seen:
                return False
            self.seen.add(key)
            self.results.append(r)
        self.on_event("result", r)
        return True

    def run(self, target):
        self.target = target.strip().lower()
        self.on_event("log", f"🌐 Deep search for: {target}  (archive_only={self.archive_only} | deep_files={self.deep_files})")

        # ── ARCHIVE-ONLY MODE — strict, no third-party sources ────────────
        if self.archive_only:
            self.on_event("log", "  📚 ARCHIVE-ONLY MODE — Internet Archive + Wayback (text + media)")
            # 1) Wayback HTML snapshots
            self.on_event("log", "    ↳ Wayback HTML snapshots…")
            for r in WaybackMachine.search_text(target, max_results=80):
                self.add(r)
            # 2) Wayback media (images / videos / audio)
            self.on_event("log", "    ↳ Wayback media (images / video / audio)…")
            for r in WaybackMachine.search_media(target, max_results=40):
                self.add(r)
            # 3) Archive.org catalog (texts / movies / audio / images)
            self.on_event("log", "    ↳ Archive.org catalog…")
            for r in ArchiveOrg.search(target, max_results=60):
                # archive_only must NEVER pull from youtube / paste / google.
                # enforce: link must be on archive.org or web.archive.org
                lk = (r.get("link") or "").lower()
                if "archive.org" in lk or "web.archive.org" in lk:
                    self.add(r)
            self.on_event("log", f"🌐 Archive-only done — {len(self.results)} archived results")
            return self.results

        queries = DorkEngine.dorks_for_name(target) + [
            f'"{target}" profile',
            f'"{target}" filetype:pdf',
            f'"{target}" bio',
            f'"{target}" contact',
            f'"{target}" social media',
            f'"{target}" news',
            f'"{target}" interview',
        ]
        engines = [
            (DDG.text, "DDG"), (Bing.search, "Bing"), (Brave.search, "Brave"),
            (Mojeek.search, "Mojeek"), (Qwant.search, "Qwant"), (Yandex.search, "Yandex"),
            (Startpage.search, "Startpage"), (Searx.search, "SearXNG"), (Ecosia.search, "Ecosia"),
            (Wikipedia.search, "Wiki"), (RedditAPI.search, "Reddit"),
            (Lemmy.search, "Lemmy"), (Mastodon.search, "Masto"), (Ahmia.search, "Tor"),
        ]
        for q in queries:
            self.on_event("log", f"  🔍 {q[:70]}")
            with ThreadPoolExecutor(max_workers=8) as ex:
                futs = [ex.submit(fn, q, 20) for fn, _ in engines]
                for f in as_completed(futs):
                    try:
                        for r in (f.result() or []):
                            self.add(r)
                    except Exception:
                        pass
            for r in DDG.images(q, 15):
                self.add(r)
            for r in DDG.videos(q, 10):
                self.add(r)
            time.sleep(0.2)

        if self.use_archive:
            self.on_event("log", "  📚 Searching Internet Archive...")
            for r in WaybackMachine.search_text(target, max_results=20):
                self.add(r)
            for r in ArchiveOrg.search(target, max_results=15):
                self.add(r)

        self.on_event("log", "  🔥 Checking paste sites...")
        for r in PasteSites.search(target, max_results=15):
            self.add(r)

        if self.deep_files:
            self.on_event("log", "  📄 Deep file & document scan (PDF/DOCX/XLSX/OCR)...")
            try:
                for r in DocumentCrawler.search_files(target, max_results=25, on_event=self.on_event):
                    self.add(r)
            except Exception as _e:
                self.on_event("log", f"  ⚠️ file scan err: {_e}")

        self.on_event("log", f"🌐 Deep search done — {len(self.results)} results")
        return self.results


# ═══════════════════════════════════════════════════════════════════════
#  AI ANALYSIS — Enhanced Arabic Intelligence Report
# ═══════════════════════════════════════════════════════════════════════
def ai_analysis(target, mode, items):
    if not AI_API_KEY or len(items) < 2:
        return ""
    try:
        condensed = []
        for it in items[:150]:
            condensed.append({
                "source": it.get("source") or it.get("platform"),
                "type": it.get("type"),
                "title": (it.get("title") or it.get("name") or "")[:120],
                "snippet": (it.get("snippet") or it.get("bio") or "")[:200],
                "link": (it.get("link") or it.get("url") or "")[:200],
                "followers": it.get("followers", ""),
                "country": it.get("country", ""),
                "relevance_score": it.get("relevance_score", ""),
            })
        mode_labels = {
            "username": "مسح اسم المستخدم",
            "phone": "تحليل رقم الهاتف",
            "email": "استخبارات البريد الإلكتروني",
            "domain": "استخبارات النطاق",
            "deep": "البحث العميق الشامل",
            "name": "تحليل الهوية الشخصية",
            "image": "بحث عكسي للصور",
        }
        prompt = (
            f"أنت محلل استخبارات سيبرانية رفيع المستوى تعمل لصالح وحدة استخبارات متقدمة. "
            f"اصنع تقريراً استخباراتياً شاملاً ومهنياً بالعربية عن الهدف «{target}» "
            f"(نوع التحليل: {mode_labels.get(mode, mode)}).\n\n"
            f"نظّم التقرير في الأقسام التالية:\n"
            f"━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
            f"⚡ الملخص التنفيذي\n"
            f"  — النتائج الرئيسية في 3-5 نقاط حادة وموجزة\n"
            f"  — مستوى التهديد: [منخفض/متوسط/عالٍ/حرج]\n\n"
            f"🌐 الحضور الرقمي المؤكد\n"
            f"  — المنصات المؤكدة مع الروابط المباشرة\n"
            f"  — أنماط النشاط والتفاعل\n\n"
            f"👤 تحليل الهوية والشبكة\n"
            f"  — المعلومات الشخصية المستخلصة\n"
            f"  — الروابط والاتصالات المحتملة\n"
            f"  — الاسم الكامل المحتمل إن وُجد\n\n"
            f"🔥 المخاطر والبيانات المكشوفة\n"
            f"  — التسريبات والاختراقات المكتشفة\n"
            f"  — البيانات الحساسة المرصودة\n\n"
            f"📍 المؤشرات الجغرافية والزمنية\n"
            f"  — الموقع الجغرافي المحتمل\n"
            f"  — التوقيت والنشاط الزمني\n\n"
            f"🎯 توصيات الاستخبارات\n"
            f"  — الخطوات التحقيقية التالية\n"
            f"  — مستوى الموثوقية والثقة\n\n"
            f"البيانات المرصودة ({len(condensed)} مصدر):\n"
            f"{json.dumps(condensed, ensure_ascii=False)[:14000]}"
        )
        r = requests.post(
            "https://openrouter.ai/api/v1/chat/completions",
            headers={
                "Authorization": f"Bearer {AI_API_KEY}",
                "Content-Type": "application/json",
                "HTTP-Referer": "https://alkaser-nexus.local",
                "X-Title": "ALKASER OSINT",
            },
            json={
                "model": AI_MODEL,
                "messages": [{"role": "user", "content": prompt}],
                "temperature": 0.3,
                "max_tokens": 3000,
            },
            timeout=75,
            verify=False,
        )
        if r.status_code == 200:
            j = r.json()
            return j["choices"][0]["message"]["content"]
    except Exception as e:
        logging.debug(f"ai: {e}")
    return ""


# ═══════════════════════════════════════════════════════════════════════
#  HTML REPORT BUILDER — DARK MATTER Theme
# ═══════════════════════════════════════════════════════════════════════
def build_html_report(target, mode, items):
    ai_text = ""
    other = []
    cards = []
    for r in items[:1000]:
        if r.get("type") == "ai_report":
            ai_text = r.get("bio") or r.get("snippet") or r.get("title", "")
            continue
        other.append(r)
        src = html.escape(str(r.get("source") or r.get("platform") or r.get("type") or "?"))
        title = html.escape(str(r.get("title") or r.get("name") or r.get("platform") or "")[:160])
        link = html.escape(str(r.get("link") or r.get("url") or r.get("image_url") or ""))
        snip = html.escape(str(r.get("snippet") or r.get("bio") or r.get("description") or "")[:300])
        thumb = r.get("thumbnail") or r.get("avatar") or r.get("image_url") or ""
        thumb = html.escape(str(thumb)) if thumb else ""
        img_tag = f'<div class="ci"><img src="{thumb}" alt="" onerror="this.parentNode.style.display=\'none\'"></div>' if thumb else ""
        score = r.get("relevance_score", "")
        score_tag = f'<div class="sc">REL: {score:.2f}</div>' if isinstance(score, float) else ""
        link_tag = f'<a href="{link}" target="_blank" rel="noopener">↗ {link[:60]}</a>' if link else ""
        cards.append(
            f'<article class="card" data-source="{src}">'
            f'{img_tag}'
            f'<div class="cb">'
            f'<span class="badge">{src}</span>'
            f'<h3>{title}</h3>'
            f'<p>{snip}</p>'
            f'{score_tag}{link_tag}'
            f'</div></article>'
        )

    sources = sorted({(r.get("source") or r.get("platform") or r.get("type") or "?") for r in other})
    btns = ['<button class="fb active" data-f="all">ALL</button>']
    for s in sources:
        btns.append(f'<button class="fb" data-f="{html.escape(str(s))}">{html.escape(str(s))}</button>')

    ai_section = ""
    if ai_text:
        ai_html = html.escape(ai_text).replace("\n", "<br>")
        ai_section = f"""
        <div class="ai-block">
          <div class="ai-hdr"><span>◈</span><h2>INTELLIGENCE REPORT — {html.escape(target)}</h2></div>
          <div class="ai-body">{ai_html}</div>
        </div>"""

    return f"""<!DOCTYPE html>
<html lang="ar" dir="rtl">
<head><style>
:root{{--bg:#050807;--panel:#0d1411;--accent:#00ff66;--danger:#ff2e2e;--text:#c8ffd9;--border:#143d24;}}
html,body{{background:#050807!important;color:#c8ffd9!important;font-family:'JetBrains Mono','Courier New',monospace!important;}}
h1,h2,h3{{color:#00ff66!important;text-shadow:0 0 8px rgba(0,255,102,.55),0 0 18px rgba(0,255,102,.25)!important;letter-spacing:.14em;text-transform:uppercase;}}
.card,.section,.item,article{{background:linear-gradient(180deg,rgba(8,16,12,.95),rgba(3,6,5,.95))!important;border:1px solid #143d24!important;color:#c8ffd9!important;}}
.leak,.breach,[data-type="leak"],[data-type="email_leak"]{{border:1px solid #ff2e2e!important;box-shadow:0 0 12px rgba(255,46,46,.55)!important;background:linear-gradient(180deg,rgba(40,4,4,.9),rgba(8,2,2,.9))!important;}}
a{{color:#00ff66!important;}}a:hover{{text-shadow:0 0 8px rgba(0,255,102,.55);}}
body::before{{content:"";position:fixed;inset:0;pointer-events:none;background:repeating-linear-gradient(0deg,rgba(0,255,102,.04) 0 1px,transparent 1px 4px);}}
</style>
<meta charset="utf-8">
<title>ALKASER NEXUS v50 — {html.escape(target)}</title>
<meta name="viewport" content="width=device-width,initial-scale=1">
<link href="https://fonts.googleapis.com/css2?family=IBM+Plex+Mono:wght@400;500;700&family=IBM+Plex+Sans+Arabic:wght@300;400;600&display=swap" rel="stylesheet">
<style>
*{{box-sizing:border-box;margin:0;padding:0}}
:root{{
  --bg:#020305;--surface:#07090f;--border:#0e1520;--border2:#1a2535;
  --acc:#3b82f6;--acc2:#60a5fa;--red:#ef4444;--amber:#f59e0b;--green:#22c55e;
  --txt:#8ba4c0;--txt2:#4a6280;--txt3:#2a4060;
  --font:'IBM Plex Mono',monospace;
}}
body{{background:var(--bg);color:var(--txt);font-family:var(--font);padding:28px;min-height:100vh;
  background-image:radial-gradient(ellipse at 15% 40%,rgba(59,130,246,.06) 0%,transparent 55%),
  radial-gradient(ellipse at 85% 20%,rgba(96,165,250,.04) 0%,transparent 55%);}}
h1{{font-size:1.5rem;color:var(--acc2);letter-spacing:.2em;text-align:center;margin-bottom:6px;font-weight:700}}
.meta{{color:var(--txt3);font-size:.8rem;text-align:center;letter-spacing:.1em;
  margin-bottom:28px;padding-bottom:14px;border-bottom:1px solid var(--border)}}
.ai-block{{background:var(--surface);border:1px solid var(--acc)30;border-radius:6px;
  margin-bottom:28px;overflow:hidden}}
.ai-hdr{{background:rgba(59,130,246,.08);color:var(--acc2);padding:14px 22px;
  display:flex;gap:12px;align-items:center;border-bottom:1px solid var(--acc)20;font-size:.85rem}}
.ai-hdr span{{font-size:1.4rem}}.ai-hdr h2{{font-weight:700;letter-spacing:.1em}}
.ai-body{{padding:24px;color:var(--txt);line-height:2;text-align:right;font-size:.85rem;
  max-height:800px;overflow-y:auto}}
.filters{{display:flex;flex-wrap:wrap;gap:7px;margin-bottom:16px;justify-content:center}}
.fb{{background:var(--surface);color:var(--txt3);border:1px solid var(--border);
  padding:5px 13px;border-radius:14px;cursor:pointer;font-family:var(--font);
  font-size:.72rem;transition:.15s;text-transform:uppercase;letter-spacing:.07em}}
.fb:hover,.fb.active{{background:var(--acc);color:#fff;border-color:var(--acc);font-weight:700}}
.search{{width:100%;max-width:480px;padding:10px 16px;border:1px solid var(--border);
  background:var(--surface);color:var(--acc2);border-radius:4px;
  font-family:var(--font);margin:0 auto 22px;display:block;outline:none;font-size:.85rem}}
.search:focus{{border-color:var(--acc)60;box-shadow:0 0 0 2px var(--acc)12}}
.grid{{display:grid;gap:14px;grid-template-columns:repeat(auto-fill,minmax(280px,1fr))}}
/* FIXED CARD HEIGHT — 220px strict */
.card{{
  background:var(--surface);border:1px solid var(--border);border-radius:6px;
  overflow:hidden;transition:.2s;
  display:flex;flex-direction:column;
  height:220px;min-height:220px;max-height:220px;
}}
.card:hover{{border-color:var(--acc)40;box-shadow:0 4px 20px rgba(59,130,246,.08);transform:translateY(-2px)}}
.ci{{width:100%;height:80px;min-height:80px;max-height:80px;overflow:hidden;flex-shrink:0;
  background:rgba(14,21,32,.8)}}
.ci img{{width:100%;height:100%;object-fit:cover;display:block}}
.cb{{padding:10px 12px;display:flex;flex-direction:column;gap:4px;flex:1;overflow:hidden}}
.badge{{display:inline-block;background:rgba(59,130,246,.1);color:var(--acc2);
  font-size:.66rem;padding:2px 8px;border-radius:8px;border:1px solid rgba(59,130,246,.2);
  letter-spacing:.1em;text-transform:uppercase;font-weight:700;flex-shrink:0}}
.cb h3{{font-size:.82rem;color:#c8ddf5;font-weight:600;
  overflow:hidden;display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;
  flex-shrink:0}}
.cb p{{color:var(--txt2);font-size:.75rem;line-height:1.5;flex:1;
  overflow:hidden;display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical}}
.sc{{font-size:.7rem;color:var(--amber);flex-shrink:0}}
.cb a{{color:var(--acc);text-decoration:none;font-size:.72rem;
  border-top:1px solid var(--border);padding-top:5px;margin-top:auto;
  display:block;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;flex-shrink:0}}
.cb a:hover{{color:var(--acc2)}}
</style>
</head>
<body>
<h1>◈ ALKASER NEXUS v50 — {html.escape(target)}</h1>
<div class="meta">
  MODE: <b>{html.escape(mode).upper()}</b> &nbsp;·&nbsp;
  {datetime.now().strftime("%Y-%m-%d %H:%M:%S")} &nbsp;·&nbsp;
  ITEMS: <b>{len(other)}</b>
</div>
{ai_section}
<input class="search" id="q" placeholder="filter results..." oninput="s()">
<div class="filters">{''.join(btns)}</div>
<section class="grid" id="g">{''.join(cards)}</section>
<script>
const c=[...document.querySelectorAll('.card')];
document.querySelectorAll('.fb').forEach(b=>b.onclick=()=>{{
  document.querySelectorAll('.fb').forEach(x=>x.classList.remove('active'));
  b.classList.add('active');const f=b.dataset.f;
  c.forEach(x=>x.style.display=(f==='all'||x.dataset.source===f)?'':'none');
}});
function s(){{const q=document.getElementById('q').value.toLowerCase();
  c.forEach(x=>x.style.display=x.innerText.toLowerCase().includes(q)?'':'none');}}
// Auto-scroll to newest cards
document.getElementById('g').scrollTop=document.getElementById('g').scrollHeight;
</script>
</body></html>"""


def save_report(target, mode, items):
    ts = datetime.now().strftime("%Y%m%d_%H%M%S")
    safe_t = re.sub(r"[^\w\-\.]", "_", target)[:50]
    base = REPORTS_DIR / f"nexus_{mode}_{safe_t}_{ts}"
    html_path = base.with_suffix(".html")
    json_path = base.with_suffix(".json")
    html_path.write_text(build_html_report(target, mode, items), encoding="utf-8")
    json_path.write_text(json.dumps(items, ensure_ascii=False, indent=2, default=str), encoding="utf-8")
    return html_path, json_path


# ═══════════════════════════════════════════════════════════════════════
#  FASTAPI APP + DARK MATTER OPERATIONS CENTER
# ═══════════════════════════════════════════════════════════════════════
try:
    from fastapi import FastAPI, WebSocket, WebSocketDisconnect, UploadFile, File, Form
    from fastapi.responses import HTMLResponse, JSONResponse, FileResponse
    import uvicorn
except ImportError:
    print("\n[!] pip install fastapi uvicorn[standard] requests beautifulsoup4 ddgs lxml python-multipart nest_asyncio\n")
    sys.exit(1)



# ═══════════════════════════════════════════════════════════════════════
#  DOCUMENT CRAWLER — Deep file/document/OCR search
# ═══════════════════════════════════════════════════════════════════════
try:
    import PyPDF2 as _PyPDF2
    HAS_PYPDF2 = True
except Exception:
    HAS_PYPDF2 = False

try:
    import docx as _docx
    HAS_DOCX = True
except Exception:
    HAS_DOCX = False

try:
    import openpyxl as _openpyxl
    HAS_XLSX = True
except Exception:
    HAS_XLSX = False

try:
    import pytesseract as _pytesseract
    from PIL import Image as _PILImage
    HAS_OCR = True
except Exception:
    HAS_OCR = False


class DocumentCrawler:
    """Find files via dorks, download (size-capped), extract text, match query."""

    MAX_BYTES = 10 * 1024 * 1024  # 10 MB
    FILE_DORKS = [
        'filetype:pdf', 'filetype:doc', 'filetype:docx',
        'filetype:xls', 'filetype:xlsx', 'filetype:ppt', 'filetype:pptx',
        'filetype:txt', 'filetype:csv', 'filetype:epub',
    ]

    @staticmethod
    def search_files(query: str, max_results: int = 25, on_event=None) -> List[dict]:
        results: List[dict] = []
        seen: Set[str] = set()
        for ft in DocumentCrawler.FILE_DORKS:
            dork = f'"{query}" {ft}'
            for engine in (DDG.text, Bing.search):
                try:
                    for r in engine(dork, 6):
                        link = (r.get("link") or "").strip()
                        if not link or link in seen:
                            continue
                        seen.add(link)
                        extracted = DocumentCrawler.download_and_extract(link, query)
                        if extracted:
                            r["type"] = "document_hit"
                            r["source"] = r.get("source", "FileCrawler")
                            r["snippet"] = extracted[:400]
                            r["file_type"] = ft.split(":")[-1]
                            results.append(r)
                            if on_event:
                                on_event("result", r)
                            if len(results) >= max_results:
                                return results
                except Exception:
                    continue
        # OCR image search
        if HAS_OCR:
            try:
                for img in DDG.images(f'"{query}"', 8):
                    iu = img.get("image_url") or img.get("link")
                    if not iu or iu in seen:
                        continue
                    seen.add(iu)
                    txt = DocumentCrawler._ocr_image(iu)
                    if txt and query.lower() in txt.lower():
                        img["type"] = "image_ocr_hit"
                        img["source"] = "OCR"
                        img["snippet"] = txt[:300]
                        results.append(img)
                        if on_event:
                            on_event("result", img)
            except Exception:
                pass
        return results

    @staticmethod
    def download_and_extract(url: str, query: str) -> Optional[str]:
        try:
            r = safe_get(url, timeout=15)
            if not r or r.status_code != 200:
                return None
            content = r.content[:DocumentCrawler.MAX_BYTES]
            text = ""
            lurl = url.lower()
            if lurl.endswith(".pdf") and HAS_PYPDF2:
                try:
                    reader = _PyPDF2.PdfReader(BytesIO(content))
                    text = " ".join((p.extract_text() or "") for p in reader.pages[:30])
                except Exception:
                    pass
            elif lurl.endswith(".docx") and HAS_DOCX:
                try:
                    d = _docx.Document(BytesIO(content))
                    text = " ".join(p.text for p in d.paragraphs)
                except Exception:
                    pass
            elif lurl.endswith(".xlsx") and HAS_XLSX:
                try:
                    wb = _openpyxl.load_workbook(BytesIO(content), read_only=True, data_only=True)
                    parts = []
                    for sh in wb.worksheets[:5]:
                        for row in sh.iter_rows(values_only=True, max_row=200):
                            parts.append(" ".join(str(c) for c in row if c is not None))
                    text = " ".join(parts)
                except Exception:
                    pass
            else:
                try:
                    text = content.decode("utf-8", errors="ignore")
                except Exception:
                    return None
            if text and query.lower() in text.lower():
                idx = text.lower().find(query.lower())
                start = max(0, idx - 120)
                return text[start:start + 400]
        except Exception:
            return None
        return None

    @staticmethod
    def _ocr_image(url: str) -> str:
        try:
            r = safe_get(url, timeout=12)
            if not r or r.status_code != 200:
                return ""
            img = _PILImage.open(BytesIO(r.content))
            return _pytesseract.image_to_string(img, lang="eng+ara")
        except Exception:
            return ""


# ═══════════════════════════════════════════════════════════════════════
#  DARK WEB SEARCH — Tor / .onion engines
# ═══════════════════════════════════════════════════════════════════════
class DarkWebSearch:
    """Search across Ahmia, Torch, Dread, DarkSearch — optionally via Tor."""

    TOR_PROXY = "socks5h://127.0.0.1:9050"

    @staticmethod
    def _proxies(use_tor: bool):
        if use_tor:
            return {"http": DarkWebSearch.TOR_PROXY, "https": DarkWebSearch.TOR_PROXY}
        return None

    @staticmethod
    def _fetch(url: str, use_tor: bool, timeout: int = 25):
        try:
            return safe_get(url, timeout=timeout, proxies=DarkWebSearch._proxies(use_tor))
        except Exception:
            return None

    @staticmethod
    def search(query: str, max_results: int = 30, use_tor: bool = True,
               engines: Optional[List[str]] = None, on_event=None) -> List[dict]:
        engines = engines or ["ahmia", "torch", "dread", "darksearch"]
        out: List[dict] = []
        if "ahmia" in engines:
            out.extend(DarkWebSearch._ahmia(query, max_results, on_event))
        if "torch" in engines:
            out.extend(DarkWebSearch._torch(query, max_results, use_tor, on_event))
        if "dread" in engines:
            out.extend(DarkWebSearch._dread(query, max_results, use_tor, on_event))
        if "darksearch" in engines:
            out.extend(DarkWebSearch._darksearch(query, max_results, on_event))
        # Dedupe
        seen = set(); uniq = []
        for r in out:
            k = (r.get("link") or "") + "|" + (r.get("title") or "")
            if k in seen: continue
            seen.add(k); uniq.append(r)
        return uniq

    @staticmethod
    def _ahmia(query, max_results, on_event):
        out = []
        try:
            r = safe_get(f"https://ahmia.fi/search/?q={quote_plus(query)}", timeout=20)
            if r and r.status_code == 200:
                blocks = re.findall(r'<li class="result"[^>]*>(.*?)</li>', r.text, flags=re.S)
                for b in blocks[:max_results]:
                    m = re.search(r'<h4>\s*<a[^>]+href="([^"]+)"[^>]*>(.*?)</a>', b, re.S)
                    if not m: continue
                    link, title = m.group(1), re.sub(r'<[^>]+>', '', m.group(2))
                    desc_m = re.search(r'<p>(.*?)</p>', b, re.S)
                    desc = re.sub(r'<[^>]+>', '', desc_m.group(1)) if desc_m else ""
                    item = {"type": "darkweb", "source": "Ahmia",
                            "title": title.strip(), "link": link.strip(),
                            "snippet": desc.strip()[:300]}
                    out.append(item)
                    if on_event: on_event("result", item)
        except Exception:
            pass
        return out

    @staticmethod
    def _torch(query, max_results, use_tor, on_event):
        out = []
        url = f"http://torchs7vpa4w6ddmgj56yseropeo5y47ixktki57a45l7zmwxrffnnqd.onion/cgi-bin/omega/omega?P={quote_plus(query)}"
        r = DarkWebSearch._fetch(url, use_tor)
        if r and r.status_code == 200:
            for m in re.finditer(r'<dt><a href="([^"]+)"[^>]*>(.*?)</a>.*?<dd>(.*?)</dd>',
                                 r.text, re.S):
                item = {"type": "darkweb", "source": "Torch",
                        "title": re.sub(r'<[^>]+>', '', m.group(2)).strip(),
                        "link": m.group(1).strip(),
                        "snippet": re.sub(r'<[^>]+>', '', m.group(3)).strip()[:300]}
                out.append(item)
                if on_event: on_event("result", item)
                if len(out) >= max_results: break
        return out

    @staticmethod
    def _dread(query, max_results, use_tor, on_event):
        out = []
        url = f"http://dreadytofatroptsdj6io7l3xptbetj2orvkfy4z6sdzv5e3jsyl4jqd.onion/search?q={quote_plus(query)}"
        r = DarkWebSearch._fetch(url, use_tor)
        if r and r.status_code == 200:
            for m in re.finditer(r'<a class="title"[^>]+href="([^"]+)"[^>]*>(.*?)</a>',
                                 r.text, re.S):
                item = {"type": "darkweb", "source": "Dread",
                        "title": re.sub(r'<[^>]+>', '', m.group(2)).strip(),
                        "link": m.group(1).strip(),
                        "snippet": "Dread forum result"}
                out.append(item)
                if on_event: on_event("result", item)
                if len(out) >= max_results: break
        return out

    @staticmethod
    def _darksearch(query, max_results, on_event):
        out = []
        try:
            r = safe_get(f"https://darksearch.io/api/search?query={quote_plus(query)}", timeout=20)
            if r and r.status_code == 200:
                data = r.json()
                for h in (data.get("data") or [])[:max_results]:
                    item = {"type": "darkweb", "source": "DarkSearch",
                            "title": h.get("title", ""),
                            "link": h.get("link", ""),
                            "snippet": (h.get("description") or "")[:300]}
                    out.append(item)
                    if on_event: on_event("result", item)
        except Exception:
            pass
        return out

app = FastAPI(title="ALKASER OSINT", version=APP_VERSION)

# ═══════════════════════════════════════════════════════════════════════
#  DARK MATTER OPERATIONS CENTER — Complete Interface
# ═══════════════════════════════════════════════════════════════════════
INDEX_HTML = r"""<!DOCTYPE html>
<html lang="ar" dir="ltr">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>ALKASER OSINT v51 — MILITARY INTEL</title>
<link href="https://fonts.googleapis.com/css2?family=IBM+Plex+Mono:ital,wght@0,400;0,500;0,700;1,400&family=IBM+Plex+Sans:wght@300;400;600&display=swap" rel="stylesheet">
<style>
/* ══════════════════════════════════════════
   DARK MATTER — ROOT VARIABLES
══════════════════════════════════════════ */
:root {
  --void: #000308;
  --dark: #04091a;
  --bg:   #060d22;
  --bg2:  #0a142e;
  --bg3:  #0e1c3e;
  --panel:#0b1530;
  --surf: #11204a;
  --border:  #1f3563;
  --border2: #2e4f8c;
  --border3: #4271c4;

  --blue:    #2f7bff;
  --blue2:   #4d97ff;
  --blue3:   #8fc3ff;
  --blue4:   #cfe4ff;
  --cyan:    #22d3ee;
  --cyan2:   #67e8f9;
  --violet:  #a855f7;
  --violet2: #d8b4fe;
  --red:     #ef4444;
  --red2:    #ff8a8a;
  --amber:   #f59e0b;
  --amber2:  #ffd34d;
  --green:   #22c55e;
  --green2:  #6ef7a4;

  --txt:  #e6f1ff;
  --txt2: #b6d2f0;
  --txt3: #7faedf;
  --dim:  #16244a;

  --acc:  var(--blue2);
  --acc2: var(--cyan2);
  --font: 'IBM Plex Mono', monospace;
  --sans: 'IBM Plex Sans', sans-serif;
  --grid: rgba(77,151,255,.07);
}

*, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }
html, body {
  height: 100%; background: var(--void); color: var(--txt);
  font-family: var(--font); font-size: 13.5px; font-weight: 500; overflow: hidden;
  -webkit-font-smoothing: antialiased; text-shadow: 0 0 1px rgba(143,195,255,.25);
}
h1,h2,h3,.brand h1,.tab,.status-pill,.threat-badge,.utc-clock { text-shadow: 0 0 6px currentColor, 0 0 14px rgba(77,151,255,.35); }
.brand h1 { color: var(--blue4) !important; }
.tab.active, .tab:hover { color: var(--cyan2) !important; box-shadow: 0 0 12px rgba(103,232,249,.35) inset; }
strong, b { color: var(--blue4); }

/* ══════════════════════════════════════════
   BACKGROUND LAYERS
══════════════════════════════════════════ */
#dm-canvas { position: fixed; inset: 0; z-index: 0; pointer-events: none; }

.dm-grid {
  position: fixed; inset: 0; z-index: 1; pointer-events: none;
  background-image:
    linear-gradient(var(--grid) 1px, transparent 1px),
    linear-gradient(90deg, var(--grid) 1px, transparent 1px);
  background-size: 48px 48px;
}

.dm-noise {
  position: fixed; inset: 0; z-index: 1; pointer-events: none;
  opacity: .018;
  background-image: url("data:image/svg+xml,%3Csvg viewBox='0 0 256 256' xmlns='http://www.w3.org/2000/svg'%3E%3Cfilter id='n'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='.9' numOctaves='4' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23n)'/%3E%3C/svg%3E");
}

.dm-vignette {
  position: fixed; inset: 0; z-index: 2; pointer-events: none;
  background: radial-gradient(ellipse at 50% 50%, transparent 30%, rgba(0,0,0,.97) 100%);
}

.dm-scanline {
  position: fixed; inset: 0; z-index: 2; pointer-events: none;
  background: repeating-linear-gradient(
    0deg, transparent 0 3px, rgba(37,99,235,.012) 3px 6px
  );
  animation: sl-drift 10s linear infinite;
}
@keyframes sl-drift { 0% { background-position: 0 0 } 100% { background-position: 0 72px } }

/* CORNER BRACKETS */
.corner { position: fixed; z-index: 3; pointer-events: none; }
.corner::before, .corner::after { content: ""; position: absolute; background: var(--blue2); opacity: .35; }
.corner.tl { top: 10px; left: 10px; }
.corner.tl::before { width: 40px; height: 1px; top: 0; left: 0; }
.corner.tl::after  { width: 1px; height: 40px; top: 0; left: 0; }
.corner.tr { top: 10px; right: 10px; }
.corner.tr::before { width: 40px; height: 1px; top: 0; right: 0; }
.corner.tr::after  { width: 1px; height: 40px; top: 0; right: 0; }
.corner.bl { bottom: 10px; left: 10px; }
.corner.bl::before { width: 40px; height: 1px; bottom: 0; left: 0; }
.corner.bl::after  { width: 1px; height: 40px; bottom: 0; left: 0; }
.corner.br { bottom: 10px; right: 10px; }
.corner.br::before { width: 40px; height: 1px; bottom: 0; right: 0; }
.corner.br::after  { width: 1px; height: 40px; bottom: 0; right: 0; }

/* SWEEP LINE */
.sweep-line {
  position: fixed; left: 0; right: 0; height: 1px; z-index: 3; pointer-events: none;
  background: linear-gradient(90deg, transparent 0%, var(--blue2) 50%, transparent 100%);
  opacity: .3; animation: sweep 12s linear infinite;
}
@keyframes sweep { 0% { top: -2px; opacity: .5 } 100% { top: 100%; opacity: 0 } }

/* ══════════════════════════════════════════
   LAYOUT
══════════════════════════════════════════ */
.shell {
  position: relative; z-index: 4;
  display: grid;
  grid-template-rows: 52px 1fr;
  height: 100vh; overflow: hidden;
}

/* ══════════════════════════════════════════
   HEADER — DARK MATTER BANNER
══════════════════════════════════════════ */
header {
  display: flex; align-items: center; gap: 14px; padding: 0 20px;
  background: rgba(3,6,13,.98);
  border-bottom: 1px solid var(--border);
  position: relative; overflow: hidden; flex-shrink: 0;
}
header::after {
  content: ""; position: absolute; bottom: 0; left: 0; right: 0; height: 1px;
  background: linear-gradient(90deg, transparent, var(--blue) 25%, var(--cyan) 75%, transparent);
  animation: hdr-pulse 6s ease infinite;
}
@keyframes hdr-pulse { 0%,100%{opacity:.2} 50%{opacity:.8} }

.cls-banner {
  position: absolute; top: 0; left: 50%; transform: translateX(-50%);
  font-size: 7px; letter-spacing: .35em; color: var(--red2); text-transform: uppercase;
  padding: 2px 18px; background: rgba(220,38,38,.08);
  border: 1px solid rgba(220,38,38,.18); border-top: none;
  border-radius: 0 0 3px 3px; font-family: var(--font); white-space: nowrap;
}

/* LOGO */
.logo-wrap { display: flex; align-items: center; gap: 11px; flex-shrink: 0; }
.logo-mark {
  width: 38px; height: 38px; flex-shrink: 0;
  background: linear-gradient(135deg, var(--blue)20, var(--blue)08);
  border: 1px solid var(--blue)40;
  border-radius: 4px;
  display: grid; place-items: center;
  font-size: 18px; color: var(--blue3);
  position: relative; overflow: hidden;
  box-shadow: 0 0 20px var(--blue)20, inset 0 0 10px var(--blue)08;
}
.logo-mark::before {
  content: "";
  position: absolute; inset: 0;
  background: linear-gradient(135deg, var(--blue)10, transparent);
  animation: logo-shimmer 4s ease infinite;
}
@keyframes logo-shimmer { 0%,100%{opacity:0} 50%{opacity:1} }
.logo-mark span { position: relative; z-index: 1; font-weight: 700; }

.brand h1 {
  font-family: var(--font); font-size: 13px; font-weight: 700; letter-spacing: .22em;
  color: var(--blue3);
}
.brand small {
  font-size: 7.5px; color: var(--txt3); letter-spacing: .18em; text-transform: uppercase; display: block;
}

/* STATUS CLUSTER */
.hdr-status { display: flex; align-items: center; gap: 8px; margin-left: 18px; }
.status-pill {
  display: flex; align-items: center; gap: 6px; padding: 4px 12px;
  border: 1px solid var(--border2); background: rgba(37,99,235,.04);
  border-radius: 14px; font-size: 9px; color: var(--blue3);
  letter-spacing: .1em; text-transform: uppercase;
}
.s-dot {
  width: 6px; height: 6px; border-radius: 50%;
  background: var(--green2); box-shadow: 0 0 8px var(--green2);
  animation: dot-pulse 1.6s ease infinite; flex-shrink: 0;
}
.s-dot.active { background: var(--amber2); box-shadow: 0 0 8px var(--amber2); }
.s-dot.scanning { background: var(--red2); box-shadow: 0 0 8px var(--red2); animation: dot-blink .8s infinite; }
@keyframes dot-pulse { 0%,100%{opacity:1;transform:scale(1)} 50%{opacity:.3;transform:scale(.65)} }
@keyframes dot-blink { 50%{opacity:.2} }

.utc-clock {
  padding: 4px 12px; border: 1px solid var(--border2);
  background: rgba(14,165,233,.04); border-radius: 3px;
  font-size: 11px; color: var(--cyan2); letter-spacing: .12em;
  font-family: var(--font);
}

.threat-badge {
  padding: 4px 12px; border-radius: 3px;
  font-size: 8px; letter-spacing: .15em; text-transform: uppercase;
  border: 1px solid var(--border2); transition: .5s;
}
.threat-badge.green  { color: var(--green2);  border-color: rgba(74,222,128,.2);  background: rgba(74,222,128,.05); }
.threat-badge.amber  { color: var(--amber2);  border-color: rgba(251,191,36,.2);  background: rgba(251,191,36,.05); }
.threat-badge.red    { color: var(--red2);    border-color: rgba(248,113,113,.2); background: rgba(248,113,113,.05); animation: threat-flash 1s infinite; }
@keyframes threat-flash { 50%{opacity:.5} }

/* MODE TABS */
.mode-tabs { display: flex; gap: 2px; margin-left: auto; flex-wrap: nowrap; }
.tab {
  padding: 6px 11px; background: transparent;
  border: 1px solid var(--border); color: var(--txt3);
  font-family: var(--font); font-size: 8px; letter-spacing: .08em;
  border-radius: 2px; cursor: pointer; text-transform: uppercase;
  transition: .18s; white-space: nowrap; position: relative;
}
.tab::after {
  content: ""; position: absolute; bottom: -1px; left: 0; right: 0;
  height: 2px; background: var(--blue2); transform: scaleX(0); transition: .22s;
}
.tab:hover { color: var(--txt); border-color: var(--border2); }
.tab:hover::after, .tab.active::after { transform: scaleX(1); }
.tab.active { background: rgba(37,99,235,.08); border-color: rgba(59,130,246,.35); color: var(--blue3); }

/* ══════════════════════════════════════════
   MAIN 3-COLUMN GRID
══════════════════════════════════════════ */
main {
  display: grid;
  grid-template-columns: 290px 1fr 270px;
  overflow: hidden; height: 100%;
}
@media (max-width: 1200px) { main { grid-template-columns: 270px 1fr 250px } }

/* ══════════════════════════════════════════
   LEFT PANEL — CONTROL TOWER
══════════════════════════════════════════ */
.ctrl-panel {
  background: rgba(5,8,15,.97);
  border-right: 1px solid var(--border);
  padding: 14px 13px;
  overflow-y: auto; display: flex; flex-direction: column; gap: 11px;
  scrollbar-width: thin; scrollbar-color: var(--border) transparent;
}
.ctrl-panel::-webkit-scrollbar { width: 3px; }
.ctrl-panel::-webkit-scrollbar-thumb { background: var(--border); }

/* SECTION HEADERS */
.sec-hdr {
  font-size: 8px; letter-spacing: .24em; color: var(--cyan2);
  text-transform: uppercase; display: flex; align-items: center; gap: 7px;
  padding: 5px 0 4px; border-bottom: 1px solid var(--border);
  font-family: var(--font);
}
.sec-hdr::before { content: "//"; color: var(--blue); font-size: 9px; }
.sec-num { color: var(--txt3); font-size: 7px; margin-left: auto; }

/* FIELDS */
.field { display: flex; flex-direction: column; gap: 4px; }
.field label { font-size: 8px; color: var(--txt3); letter-spacing: .16em; text-transform: uppercase; }
.field input, .field select {
  width: 100%; background: rgba(3,6,13,.9); border: 1px solid var(--border);
  color: var(--blue3); padding: 8px 11px; border-radius: 2px;
  font-family: var(--font); font-size: 11.5px; outline: none; transition: .18s;
  letter-spacing: .04em;
}
.field input:focus, .field select:focus {
  border-color: rgba(59,130,246,.5);
  box-shadow: 0 0 0 2px rgba(59,130,246,.1), inset 0 0 10px rgba(59,130,246,.03);
}
.field input::placeholder { color: var(--txt3); }

/* INFO BLOCKS */
.mode-info {
  font-size: 8.5px; color: var(--cyan2); line-height: 2;
  padding: 6px 0; display: none;
}
.mode-info.active { display: block; }
.mi-item { display: flex; align-items: center; gap: 6px; color: var(--txt2); }
.mi-item::before { content: "▸"; color: var(--blue); flex-shrink: 0; }
.mi-ok { color: var(--green2) !important; }
.mi-ok::before { content: "✓"; color: var(--green2) !important; }

/* IMAGE UPLOAD */
.img-zone { display: none; flex-direction: column; gap: 8px; }
.img-zone.active { display: flex; }
.upload-area {
  border: 1px dashed var(--border2); border-radius: 3px;
  padding: 14px; text-align: center; cursor: pointer;
  color: var(--txt2); font-size: 9px; letter-spacing: .1em;
  transition: .18s; background: rgba(3,6,13,.5);
}
.upload-area:hover { border-color: var(--blue2); color: var(--blue3); }
.img-preview {
  width: 100%; max-height: 100px; object-fit: contain;
  border: 1px solid var(--border); border-radius: 2px; display: none;
}
.upload-name { font-size: 8px; color: var(--txt3); }

/* TOGGLE SWITCHES */
.toggle-row { display: flex; flex-direction: column; gap: 7px; }
.toggle {
  display: flex; align-items: center; justify-content: space-between;
  cursor: pointer; user-select: none; padding: 2px 0;
}
.toggle-label { color: var(--txt2); font-size: 10px; letter-spacing: .04em; }
.toggle-sw {
  width: 32px; height: 16px; border-radius: 8px;
  background: var(--bg3); border: 1px solid var(--border2);
  position: relative; cursor: pointer; transition: .2s; flex-shrink: 0;
}
.toggle-sw::after {
  content: ""; position: absolute;
  width: 11px; height: 11px; border-radius: 50%;
  background: var(--txt3); top: 2px; left: 2px; transition: .2s;
}
input[type=checkbox]:checked + .toggle-sw {
  background: var(--blue); border-color: rgba(59,130,246,.5);
  box-shadow: 0 0 8px rgba(59,130,246,.3);
}
input[type=checkbox]:checked + .toggle-sw::after {
  left: 17px; background: #fff;
}
input[type=checkbox].hidden { display: none; }

/* BUTTONS */
.btn-exec {
  width: 100%; padding: 11px; border: none; cursor: pointer;
  font-family: var(--font); font-weight: 700; font-size: 9px;
  letter-spacing: .2em; text-transform: uppercase; border-radius: 3px;
  background: linear-gradient(135deg, var(--blue), #1d4ed8);
  color: #fff; box-shadow: 0 0 24px rgba(37,99,235,.35);
  position: relative; overflow: hidden; transition: .22s;
}
.btn-exec::before {
  content: ""; position: absolute; inset: 0;
  background: linear-gradient(90deg, transparent, rgba(255,255,255,.15), transparent);
  transform: translateX(-100%); transition: .5s;
}
.btn-exec:hover::before { transform: translateX(100%); }
.btn-exec:hover {
  background: linear-gradient(135deg, var(--blue2), var(--blue));
  box-shadow: 0 0 40px rgba(37,99,235,.5);
}
.btn-exec:disabled {
  background: rgba(10,16,32,.9); color: var(--txt3);
  box-shadow: none; cursor: not-allowed;
}
.btn-stop {
  width: 100%; padding: 8px; border-radius: 3px;
  font-family: var(--font); font-size: 9px; letter-spacing: .14em;
  text-transform: uppercase; background: transparent; cursor: pointer;
  color: var(--red2); border: 1px solid rgba(220,38,38,.3); transition: .18s;
}
.btn-stop:hover:not(:disabled) {
  background: var(--red); color: #fff;
  box-shadow: 0 0 18px rgba(220,38,38,.4);
}
.btn-stop:disabled { color: var(--txt3); border-color: var(--border); cursor: not-allowed; }
.btn-util {
  width: 100%; padding: 8px; border-radius: 3px;
  font-family: var(--font); font-size: 10px; letter-spacing: .06em;
  background: transparent; color: var(--txt2);
  border: 1px solid var(--border); transition: .15s; cursor: pointer;
}
.btn-util:hover { border-color: var(--border3); color: var(--cyan2); }

/* PROGRESS */
.prog-track { position: relative; height: 2px; background: var(--border); border-radius: 1px; overflow: hidden; }
.prog-fill {
  height: 100%; width: 0%;
  background: linear-gradient(90deg, var(--blue), var(--cyan2));
  transition: width .4s ease;
  box-shadow: 0 0 12px var(--blue2);
}
.prog-labels { display: flex; justify-content: space-between; font-size: 8px; color: var(--txt3); margin-top: 5px; }
.prog-pct { color: var(--blue3); }

/* ══════════════════════════════════════════
   RADAR / STATS
══════════════════════════════════════════ */
.radar-wrap { display: flex; flex-direction: column; align-items: center; gap: 10px; }
.radar-ring {
  width: 140px; height: 140px; border-radius: 50%;
  border: 1px solid rgba(37,99,235,.25);
  position: relative;
  background:
    radial-gradient(circle, rgba(37,99,235,.07) 0%, transparent 65%),
    repeating-radial-gradient(circle at 50%, transparent 0, transparent 22px, rgba(37,99,235,.06) 23px, transparent 24px);
  box-shadow: 0 0 35px rgba(37,99,235,.12), inset 0 0 15px rgba(37,99,235,.04);
}
.radar-ring::before { content:""; position:absolute; inset:22%; border:1px solid rgba(37,99,235,.15); border-radius:50%; }
.radar-ring::after  { content:""; position:absolute; inset:44%; border:1px solid rgba(37,99,235,.1);  border-radius:50%; }
.radar-h { position:absolute; top:50%; left:5%; right:5%; height:1px; background:rgba(37,99,235,.12); }
.radar-v { position:absolute; left:50%; top:5%; bottom:5%; width:1px; background:rgba(37,99,235,.12); }
.radar-sweep {
  position:absolute; top:0; left:50%; width:50%; height:50%;
  transform-origin:0 100%; border-radius:100% 0 0 0;
  background:conic-gradient(from 0deg, rgba(37,99,235,.65), rgba(37,99,235,.2) 20%, transparent 55%);
  animation:r-spin 3.2s linear infinite;
}
@keyframes r-spin { to{transform:rotate(360deg)} }
.blip {
  position:absolute; border-radius:50%; background:var(--blue2);
  box-shadow:0 0 8px var(--blue2),0 0 18px rgba(59,130,246,.4);
  animation:blip-out 2.2s ease-out forwards; pointer-events:none;
}
@keyframes blip-out { 0%{opacity:1;transform:scale(1)} 100%{opacity:0;transform:scale(3.5)} }
.radar-label { font-size: 8px; color: var(--txt3); letter-spacing: .14em; text-transform: uppercase; }

/* STAT GRID */
.stat-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 5px; }
.stat-cell {
  background: var(--surf); border: 1px solid var(--border);
  padding: 9px 6px; border-radius: 3px; text-align: center; position: relative; overflow: hidden;
}
.stat-cell::after {
  content: ""; position: absolute; top: 0; left: 0; right: 0; height: 1px;
  background: linear-gradient(90deg, transparent, var(--blue)50, transparent);
}
.stat-val { font-family: var(--font); font-size: 20px; color: var(--blue3); font-weight: 700; line-height: 1; }
.stat-key { font-size: 7px; color: var(--txt3); letter-spacing: .14em; text-transform: uppercase; margin-top: 3px; }

/* ══════════════════════════════════════════
   CENTER — RESULTS PANEL
══════════════════════════════════════════ */
.results-panel { display: flex; flex-direction: column; overflow: hidden; }

/* TOP BAR */
.results-top {
  display: flex; align-items: center; gap: 7px; padding: 8px 14px;
  background: rgba(5,8,15,.98); border-bottom: 1px solid var(--border);
  flex-wrap: wrap; flex-shrink: 0; min-height: 44px;
}
.filter-input {
  flex: 1; min-width: 120px; max-width: 260px;
  background: var(--surf); border: 1px solid var(--border);
  color: var(--txt); padding: 6px 10px; border-radius: 2px;
  font-family: var(--font); font-size: 11px; outline: none; transition: .15s;
}
.filter-input:focus { border-color: rgba(59,130,246,.4); }
.filter-input::placeholder { color: var(--txt3); }

.chip-row { display: flex; flex-wrap: wrap; gap: 5px; flex: 1; }
.chip {
  padding: 3px 10px; background: transparent;
  border: 1px solid var(--border); color: var(--txt3);
  font-size: 7.5px; letter-spacing: .07em; border-radius: 12px;
  cursor: pointer; font-family: var(--font); text-transform: uppercase; transition: .14s;
  white-space: nowrap;
}
.chip:hover { border-color: rgba(59,130,246,.3); color: var(--blue3); }
.chip.active { background: var(--blue); color: #fff; border-color: var(--blue); font-weight: 700; }
.res-count { font-size: 8.5px; color: var(--txt3); letter-spacing: .08em; white-space: nowrap; margin-left: auto; }

/* CARDS GRID */
.cards-grid {
  flex: 1; overflow-y: auto; padding: 13px;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(255px, 1fr));
  gap: 10px; align-content: start;
  scrollbar-width: thin; scrollbar-color: var(--border) transparent;
}
.cards-grid::-webkit-scrollbar { width: 5px; }
.cards-grid::-webkit-scrollbar-thumb { background: var(--border); border-radius: 3px; }

/* ══════════════════════════════════════════
   INTEL CARDS — FIXED 220px HEIGHT
══════════════════════════════════════════ */
.card {
  /* STRICT: 220px — never grows, never shrinks */
  height: 220px;
  min-height: 220px;
  max-height: 220px;

  background: rgba(7,9,15,.98);
  border: 1px solid var(--border);
  border-radius: 4px;
  overflow: hidden;
  display: flex; flex-direction: column;
  position: relative;
  transition: border-color .22s, transform .22s, box-shadow .22s;
  animation: card-enter .32s ease;
}
@keyframes card-enter { from { opacity:0; transform: translateY(12px) scale(.97) } }

.card:hover {
  border-color: rgba(59,130,246,.4);
  box-shadow: 0 4px 24px rgba(37,99,235,.12);
  transform: translateY(-2px);
}

/* SCAN SWEEP on hover */
.card-scan {
  position: absolute; top: 0; left: -100%; right: 100%; height: 1px;
  background: linear-gradient(90deg, transparent, var(--blue2), transparent);
  transition: .55s ease-out;
}
.card:hover .card-scan { left: 100%; right: -100%; }

/* IMAGE SECTION — exactly 80px */
.card-img {
  width: 100%; height: 80px; min-height: 80px; max-height: 80px;
  overflow: hidden; flex-shrink: 0;
  background: var(--bg3); position: relative;
}
.card-img img {
  width: 100%; height: 100%; object-fit: cover; display: block;
  transition: transform .3s;
}
.card:hover .card-img img { transform: scale(1.06); }
.card-ph {
  position: absolute; inset: 0; display: grid; place-items: center;
  font-family: var(--font); font-size: 28px; color: rgba(37,99,235,.12);
  background: repeating-linear-gradient(
    45deg, transparent 0 8px, rgba(37,99,235,.02) 8px 16px
  );
}

/* BODY SECTION — remaining ~140px (or 60px without image) */
.card-body {
  padding: 9px 11px;
  display: flex; flex-direction: column; gap: 4px;
  flex: 1; overflow: hidden;
}

/* SOURCE BADGE */
.src-badge {
  align-self: flex-start; font-size: 7px; padding: 2px 7px; border-radius: 1px;
  letter-spacing: .1em; text-transform: uppercase; font-weight: 700;
  flex-shrink: 0; white-space: nowrap;
}
.sb-default { background: rgba(37,99,235,.1);  color: var(--blue3);   border: 1px solid rgba(37,99,235,.2); }
.sb-social   { background: rgba(124,58,237,.1); color: var(--violet2); border: 1px solid rgba(124,58,237,.2); }
.sb-leak     { background: rgba(220,38,38,.1);  color: var(--red2);    border: 1px solid rgba(220,38,38,.2); }
.sb-archive  { background: rgba(14,165,233,.1); color: var(--cyan2);   border: 1px solid rgba(14,165,233,.2); }
.sb-dark     { background: rgba(217,119,6,.1);  color: var(--amber2);  border: 1px solid rgba(217,119,6,.2); }
.sb-owner    { background: rgba(22,163,74,.1);  color: var(--green2);  border: 1px solid rgba(22,163,74,.2); }

/* TITLE — exactly 2 lines */
.card-title {
  font-size: 11px; color: #cde0f8; font-weight: 600;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  line-height: 1.4;
  flex-shrink: 0;
}

/* SNIPPET — exactly 2 lines */
.card-snip {
  font-size: 9px; color: var(--txt2); line-height: 1.5;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  flex: 1;
}

/* CARD META */
.card-meta { display: flex; gap: 6px; font-size: 7.5px; color: var(--blue3); flex-wrap: wrap; flex-shrink: 0; }

/* RELEVANCE DOTS */
.rel-row { display: flex; align-items: center; gap: 5px; font-size: 7.5px; color: var(--amber2); flex-shrink: 0; }
.rel-dots { display: flex; gap: 2px; }
.rel-dot { width: 5px; height: 5px; border-radius: 1px; background: var(--border); }
.rel-dot.on { background: var(--amber2); box-shadow: 0 0 4px var(--amber2); }

/* CONFIDENCE BAR */
.conf-bar { height: 2px; background: var(--border); border-radius: 1px; overflow: hidden; flex-shrink: 0; }
.conf-fill { height: 100%; border-radius: 1px; transition: .5s; }
.cf-high { background: var(--green2); box-shadow: 0 0 5px var(--green2); }
.cf-med  { background: var(--amber2); box-shadow: 0 0 5px var(--amber2); }
.cf-low  { background: var(--red2);   box-shadow: 0 0 5px var(--red2); }

/* LINK */
.card-link {
  color: var(--blue2); font-size: 8.5px; text-decoration: none;
  border-top: 1px solid var(--border); padding-top: 5px; margin-top: auto;
  display: block; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
  flex-shrink: 0; opacity: .75; transition: opacity .15s;
}
.card-link:hover { opacity: 1; color: var(--cyan2); }

/* SIMILARITY */
.sim-badge {
  font-size: 7.5px; padding: 1px 6px; border-radius: 1px;
  background: rgba(22,163,74,.12); color: var(--green2); border: 1px solid rgba(22,163,74,.2);
  flex-shrink: 0; align-self: flex-start;
}

/* EMPTY STATE */
.empty-state {
  grid-column: 1 / -1; text-align: center; padding: 70px 20px;
  color: var(--txt3); letter-spacing: .14em; text-transform: uppercase; font-size: 10px;
}
.empty-icon {
  font-family: var(--font); font-size: 52px; color: rgba(37,99,235,.08);
  display: block; margin-bottom: 18px; animation: dot-pulse 2.5s ease infinite;
}

/* ══════════════════════════════════════════
   RIGHT PANEL — TERMINAL + FEED
══════════════════════════════════════════ */
.term-panel {
  background: rgba(3,6,13,.98);
  border-left: 1px solid var(--border);
  display: flex; flex-direction: column; overflow: hidden;
}
.term-hdr {
  padding: 9px 14px; border-bottom: 1px solid var(--border);
  font-size: 8px; letter-spacing: .2em; color: var(--cyan2);
  text-transform: uppercase; display: flex; align-items: center; justify-content: space-between;
  background: rgba(0,10,25,.6); flex-shrink: 0; font-family: var(--font);
}
.term-live { display: flex; align-items: center; gap: 5px; font-size: 7.5px; color: var(--blue3); }
.term-body {
  flex: 1; overflow-y: auto; padding: 8px 10px;
  font-family: var(--font); font-size: 9.5px; line-height: 1.95;
  color: var(--txt2); background: rgba(0,1,6,.96);
  scrollbar-width: thin; scrollbar-color: var(--border) transparent;
}
.term-body::-webkit-scrollbar { width: 3px; }
.term-body::-webkit-scrollbar-thumb { background: var(--border); }
.tl { padding: .3px 0; word-wrap: break-word; animation: tl-slide .1s ease; }
@keyframes tl-slide { from { opacity: 0; padding-left: 4px } }
.tl .ts { color: var(--txt3); font-size: 8px; margin-right: 6px; }
.tl.ok   { color: var(--green2); }
.tl.err  { color: var(--red2); }
.tl.info { color: var(--cyan2); }
.tl.warn { color: var(--amber2); }

/* INTEL FEED */
.intel-feed {
  border-top: 1px solid var(--border); padding: 9px 12px;
  font-size: 8.5px; color: var(--txt2); flex-shrink: 0;
  background: rgba(0,5,18,.85); max-height: 105px; overflow: hidden;
}
.intel-feed strong { color: var(--blue3); }
.feed-item {
  padding: 2px 0; border-bottom: 1px solid var(--border);
  animation: feed-in .28s ease; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
@keyframes feed-in { from { opacity: 0; transform: translateY(-5px) } }

/* ══════════════════════════════════════════
   MISC BADGES
══════════════════════════════════════════ */
.intel-tag {
  display: inline-flex; align-items: center; gap: 5px; padding: 3px 8px;
  border-radius: 2px; font-size: 7.5px; letter-spacing: .1em; text-transform: uppercase;
  font-weight: 700;
}
.it-green { background: rgba(22,163,74,.1);  color: var(--green2); border: 1px solid rgba(22,163,74,.2); }
.it-blue  { background: rgba(37,99,235,.1);  color: var(--blue3);  border: 1px solid rgba(37,99,235,.2); }
.it-red   { background: rgba(220,38,38,.1);  color: var(--red2);   border: 1px solid rgba(220,38,38,.2); }
.it-amber { background: rgba(217,119,6,.1);  color: var(--amber2); border: 1px solid rgba(217,119,6,.2); }
</style>

<style id="alkaser-osint-military">
:root{
  /* ── NEON GREEN / RED-ONLY PALETTE — no blue anywhere ── */
  --bg:#020503; --bg-2:#050a07; --panel:#07120c; --panel-2:#0a1810;
  --accent:#00ff66; --accent-2:#39ff88; --accent-dim:#00cc52; --accent-glow:rgba(0,255,102,.75);
  --danger:#ff1a1a; --danger-2:#ff4d4d; --danger-glow:rgba(255,26,26,.75);
  --text:#d6ffe2; --muted:#7fa890; --border:#1d5a35;
  /* kill any leftover blue tokens from earlier themes */
  --blue:#00ff66 !important; --blue2:#00ff66 !important; --blue3:#00ff66 !important;
  --cyan:#00ff66 !important; --cyan2:#00ff66 !important;
  --primary:#00ff66 !important;
}
html,body{background:#020503 !important;color:var(--text) !important;
  font-family:'JetBrains Mono','Courier New',monospace !important;
  font-size:17px !important; line-height:1.55 !important;}
body::before{content:"";position:fixed;inset:0;pointer-events:none;z-index:1;
  background:
    repeating-linear-gradient(0deg, rgba(0,255,102,.04) 0 1px, transparent 1px 4px),
    radial-gradient(ellipse at top, rgba(0,255,102,.10), transparent 75%);}

/* GLOBAL — kill every blue/cyan/indigo color in the legacy theme */
*{border-color:var(--border) !important;}
[style*="#0ea5e9"],[style*="#1d4ed8"],[style*="#2563eb"],[style*="#3b82f6"],
[style*="#0284c7"],[style*="#06b6d4"],[style*="#22d3ee"],[style*="#7dd3fc"],
[style*="rgb(14, 165"],[style*="rgb(37, 99"],[style*="rgb(59, 130"]{
  color:var(--accent) !important; background-color:transparent !important;
  border-color:var(--accent-dim) !important;}

/* TYPOGRAPHY — bigger & clearer */
h1,h2,h3,h4,.title,.brand,.brand-title,.app-title{color:var(--accent) !important;
  text-shadow:0 0 10px var(--accent-glow), 0 0 22px rgba(0,255,102,.35) !important;
  letter-spacing:.14em;text-transform:uppercase;}
h1,.brand-title{font-size:2.1rem !important;}
h2{font-size:1.55rem !important;}
h3{font-size:1.25rem !important;}
label,.field label,.sec-hdr{font-size:.95rem !important;letter-spacing:.12em !important;
  color:var(--accent-2) !important;text-transform:uppercase;}
p,li,td,th,.snippet,.desc,.muted,small,.subtitle{font-size:1rem !important;}
.tab,.tab-btn,button{font-size:1rem !important;letter-spacing:.1em !important;}
input,select,textarea{font-size:1.05rem !important;padding:.55rem .8rem !important;}
.terminal,#terminal,.log,.console{font-size:.95rem !important;line-height:1.55 !important;}

/* BUTTONS */
button,.btn,.tab,.tab-btn{background:linear-gradient(180deg,#0a1a10,#020503) !important;
  border:1px solid var(--accent-dim) !important;color:var(--accent) !important;
  text-transform:uppercase;letter-spacing:.1em;padding:.55rem 1rem !important;
  box-shadow:inset 0 0 0 1px rgba(0,255,102,.1), 0 0 8px rgba(0,255,102,.2);
  transition:all .15s ease;}
button:hover,.btn:hover,.tab:hover,.tab-btn:hover{
  box-shadow:0 0 18px var(--accent-glow), inset 0 0 18px rgba(0,255,102,.2) !important;
  border-color:var(--accent) !important;transform:translateY(-1px);}
.tab.active,.tab-btn.active,.active{background:linear-gradient(180deg,#0d3a1d,#06200f) !important;
  color:#d6ffe2 !important;border-color:var(--accent-2) !important;
  box-shadow:0 0 24px var(--accent-glow), inset 0 0 12px rgba(0,255,102,.3) !important;}

/* INPUTS */
input,select,textarea{background:#010301 !important;color:var(--accent-2) !important;
  border:1px solid var(--border) !important;font-family:inherit !important;
  caret-color:var(--accent);}
input:focus,select:focus,textarea:focus{
  border-color:var(--accent) !important;box-shadow:0 0 14px var(--accent-glow) !important;
  outline:none !important;}
input::placeholder{color:var(--muted) !important;opacity:.8;}

/* PANELS / CARDS */
.card,.panel,.box,.result-card,.ctrl-panel,aside,.sidebar{
  background:linear-gradient(180deg, rgba(7,18,12,.96), rgba(2,5,3,.96)) !important;
  border:1px solid var(--border) !important;
  box-shadow:inset 0 0 0 1px rgba(0,255,102,.05), 0 0 12px rgba(0,255,102,.08);}
.result-card:hover{border-color:var(--accent) !important;
  box-shadow:0 0 18px var(--accent-glow);}
.result-card .title,.result-card a{color:var(--accent-2) !important;font-weight:600;}

/* LEAK / CRITICAL */
.leak,.breach,.critical,[data-type="leak"],[data-type="email_leak"]{
  border:1px solid var(--danger) !important;
  box-shadow:0 0 16px var(--danger-glow) !important;
  background:linear-gradient(180deg, rgba(50,6,6,.95), rgba(10,2,2,.95)) !important;}

/* TERMINAL */
.terminal,#terminal,.log,.console{
  background:#000 !important;color:var(--accent-2) !important;
  border:1px solid var(--accent-dim) !important;
  font-family:'JetBrains Mono',monospace !important;
  text-shadow:0 0 5px var(--accent-glow);}

/* RADAR */
.radar,#radar,.radar-circle{
  width:220px !important;height:220px !important;
  border:2px solid var(--accent) !important;border-radius:50% !important;
  box-shadow:0 0 40px var(--accent-glow), inset 0 0 40px rgba(0,255,102,.25) !important;}
.radar::after,#radar::after{background:conic-gradient(from 0deg,
  var(--accent) 0deg, transparent 60deg) !important;}

/* LINKS */
a,a:visited{color:var(--accent-2) !important;text-decoration:none;}
a:hover{text-shadow:0 0 10px var(--accent-glow);color:#fff !important;}
::selection{background:var(--accent);color:#000;}

/* STATUS PULSE */
@keyframes alk-pulse-red{0%,100%{box-shadow:0 0 8px var(--danger-glow);}
  50%{box-shadow:0 0 28px var(--danger),0 0 40px var(--danger-glow);}}
@keyframes alk-pulse-green{0%,100%{box-shadow:0 0 8px var(--accent-glow);}
  50%{box-shadow:0 0 28px var(--accent),0 0 40px var(--accent-glow);}}
.status-scanning,.scanning{animation:alk-pulse-green 1.1s ease-in-out infinite;
  color:var(--accent) !important;border-color:var(--accent) !important;}

/* ═══════════════════════════════════════════════════════════════
   DARK-WEB MODE — applied ONLY when <body class="dw-mode">
   This guarantees the red theme NEVER bleeds into other tabs.
   ═══════════════════════════════════════════════════════════════ */
body.dw-mode{background:#050000 !important;color:#ffd0d0 !important;}
body.dw-mode::before{
  background:
    repeating-linear-gradient(0deg, rgba(255,26,26,.05) 0 1px, transparent 1px 4px),
    radial-gradient(ellipse at top, rgba(255,26,26,.18), transparent 75%) !important;}
body.dw-mode *{border-color:#5a0e0e !important;}
body.dw-mode h1,body.dw-mode h2,body.dw-mode h3,body.dw-mode h4,
body.dw-mode .brand-title,body.dw-mode .title{
  color:var(--danger-2) !important;
  text-shadow:0 0 12px var(--danger-glow),0 0 24px rgba(255,26,26,.4) !important;}
body.dw-mode button,body.dw-mode .btn,body.dw-mode .tab,body.dw-mode .tab-btn{
  background:linear-gradient(180deg,#2a0707,#0a0202) !important;
  border-color:#7a1414 !important;color:var(--danger-2) !important;
  box-shadow:inset 0 0 0 1px rgba(255,26,26,.12), 0 0 10px rgba(255,26,26,.25) !important;}
body.dw-mode .tab.active,body.dw-mode .tab-btn.active{
  background:linear-gradient(180deg,#5a0a0a,#1a0202) !important;
  color:#ffe6e6 !important;border-color:var(--danger-2) !important;
  box-shadow:0 0 28px var(--danger-glow), inset 0 0 14px rgba(255,26,26,.35) !important;}
body.dw-mode input,body.dw-mode select,body.dw-mode textarea{
  background:#0a0000 !important;color:var(--danger-2) !important;
  border-color:#5a0e0e !important;caret-color:var(--danger);}
body.dw-mode input:focus{border-color:var(--danger) !important;
  box-shadow:0 0 16px var(--danger-glow) !important;}
body.dw-mode .card,body.dw-mode .panel,body.dw-mode .result-card,
body.dw-mode .ctrl-panel,body.dw-mode aside{
  background:linear-gradient(180deg, rgba(30,4,4,.96), rgba(8,1,1,.96)) !important;
  border-color:#5a0e0e !important;
  box-shadow:inset 0 0 0 1px rgba(255,26,26,.08), 0 0 14px rgba(255,26,26,.12) !important;}
body.dw-mode .terminal,body.dw-mode #terminal,body.dw-mode .log{
  background:#000 !important;color:var(--danger-2) !important;
  border-color:#7a1414 !important;text-shadow:0 0 6px var(--danger-glow);}
body.dw-mode a{color:#ff8a8a !important;}
body.dw-mode .radar,body.dw-mode #radar{
  border-color:var(--danger) !important;
  box-shadow:0 0 40px var(--danger-glow), inset 0 0 40px rgba(255,26,26,.25) !important;}
body.dw-mode .radar::after,body.dw-mode #radar::after{
  background:conic-gradient(from 0deg, var(--danger) 0deg, transparent 60deg) !important;}

/* DARK WEB tab marker (always visible, regardless of mode) */
[data-mode="darkweb"],[data-tab="darkweb"]{
  color:var(--danger-2) !important;border-color:var(--danger) !important;}
[data-mode="darkweb"].active,[data-tab="darkweb"].active{
  background:linear-gradient(180deg,#5a0a0a,#1a0202) !important;
  box-shadow:0 0 22px var(--danger-glow) !important;}
</style>

<script id="alkaser-osint-init">
(function(){
  function ready(fn){ if(document.readyState!=='loading') fn();
    else document.addEventListener('DOMContentLoaded',fn); }

  ready(function(){
    // ── 1) Title / brand text ──────────────────────────────────────
    document.title='ALKASER OSINT — Open Source Intelligence';
    document.querySelectorAll('h1,.brand,.app-title,.brand-title').forEach(function(el){
      if(/NEXUS|DARK MATTER|ALKASER/i.test(el.textContent)){
        el.textContent='ALKASER OSINT';
        el.classList.add('brand-title');
      }
    });

    // ── 2) Locate the native tab bar (.mode-tabs / [data-mode]) ───
    var nativeTab = document.querySelector('[data-mode]');
    var tabBar    = nativeTab ? nativeTab.parentNode :
                    document.querySelector('.mode-tabs,.tabs,.tab-bar');
    if(!tabBar) return;

    // ── 3) Inject DARK WEB as a real native tab ────────────────────
    if(!tabBar.querySelector('[data-mode="darkweb"]')){
      var dwBtn=document.createElement('button');
      dwBtn.className=nativeTab ? nativeTab.className : 'tab';
      dwBtn.setAttribute('data-mode','darkweb');
      dwBtn.textContent='🕸 DARK WEB';
      tabBar.appendChild(dwBtn);
    }

    // ── 4) Build hidden options panels (general + darkweb) ─────────
    var sidebar = document.querySelector('.ctrl-panel,.options,.left-panel,aside,.sidebar')
                  || document.body;
    if(!document.getElementById('alk-deep-opts')){
      var deep=document.createElement('div');
      deep.id='alk-deep-opts';
      deep.className='card panel';
      deep.style.cssText='margin-top:14px;padding:12px;font-size:1rem;';
      deep.innerHTML=''
        +'<div style="color:var(--accent);font-weight:bold;margin-bottom:8px;font-size:1.05rem;">'
        +'▣ DEEP-SEARCH FILTERS</div>'
        +'<label style="display:block;margin:6px 0;"><input type="checkbox" id="optUseArchive" checked> Include Archive.org / Wayback</label>'
        +'<label style="display:block;margin:6px 0;"><input type="checkbox" id="optArchiveOnly"> Archive-only mode (strict)</label>'
        +'<label style="display:block;margin:6px 0;"><input type="checkbox" id="optDeepFiles"> Deep file scan (PDF/DOCX/XLSX/OCR)</label>';
      sidebar.appendChild(deep);
    }
    if(!document.getElementById('alk-dw-opts')){
      var dw=document.createElement('div');
      dw.id='alk-dw-opts';
      dw.className='card panel';
      dw.style.cssText='margin-top:14px;padding:12px;font-size:1rem;display:none;';
      dw.innerHTML=''
        +'<div style="color:var(--danger-2);font-weight:bold;margin-bottom:8px;font-size:1.05rem;">'
        +'▣ DARK WEB ENGINES</div>'
        +'<label style="display:block;margin:6px 0;"><input type="checkbox" id="optUseTor" checked> Route via Tor (127.0.0.1:9050)</label>'
        +'<label style="display:block;margin:6px 0;"><input type="checkbox" id="dwAhmia" checked> Ahmia</label>'
        +'<label style="display:block;margin:6px 0;"><input type="checkbox" id="dwTorch" checked> Torch</label>'
        +'<label style="display:block;margin:6px 0;"><input type="checkbox" id="dwDread" checked> Dread</label>'
        +'<label style="display:block;margin:6px 0;"><input type="checkbox" id="dwDS" checked> DarkSearch</label>';
      sidebar.appendChild(dw);
    }

    // ── 5) Tab switching: enforce isolation ───────────────────────
    function setMode(mode){
      window.__currentMode = mode;
      document.querySelectorAll('[data-mode]').forEach(function(b){
        b.classList.toggle('active', b.getAttribute('data-mode')===mode);
      });
      var isDW = (mode==='darkweb');
      document.body.classList.toggle('dw-mode', isDW);
      var dwBox   = document.getElementById('alk-dw-opts');
      var deepBox = document.getElementById('alk-deep-opts');
      if(dwBox)   dwBox.style.display   = isDW ? 'block' : 'none';
      if(deepBox) deepBox.style.display = isDW ? 'none'  : 'block';

      // Update input label/placeholder for darkweb
      var lbl = document.getElementById('tgtLabel');
      var inp = document.getElementById('tgtInput') ||
                document.querySelector('input[type="text"]');
      if(isDW){
        if(lbl) lbl.textContent = 'Dark-Web Keyword';
        if(inp) inp.placeholder = 'keyword / leak indicator / .onion fragment…';
      } else if(mode==='deep'){
        if(lbl) lbl.textContent = 'Deep Search Target';
        if(inp) inp.placeholder = 'name, email, phone, handle…';
      }
    }

    // Bind clicks on every tab (including the dark-web one we added)
    document.querySelectorAll('[data-mode]').forEach(function(b){
      b.addEventListener('click', function(){
        setMode(b.getAttribute('data-mode'));
      }, true);
    });

    // Default mode
    var initial = (document.querySelector('[data-mode].active')||{}).getAttribute &&
                  document.querySelector('[data-mode].active').getAttribute('data-mode');
    setMode(initial || 'username');

    // ── 6) Patch WebSocket send: inject options + correct mode ────
    var origSend = WebSocket.prototype.send;
    WebSocket.prototype.send = function(data){
      try{
        var o = (typeof data==='string') ? JSON.parse(data) : null;
        if(o && o.action==='start'){
          var dw = (window.__currentMode==='darkweb');
          if(dw){ o.mode = 'darkweb'; }
          // Deep / archive flags only matter outside dark-web mode
          o.use_archive  = !dw && !!(document.getElementById('optUseArchive')||{}).checked;
          o.archive_only = !dw && !!(document.getElementById('optArchiveOnly')||{}).checked;
          o.deep_files   = !dw && !!(document.getElementById('optDeepFiles')||{}).checked;
          // Dark-web flags only matter inside dark-web mode
          o.use_tor    = dw && !!(document.getElementById('optUseTor')||{}).checked;
          o.dw_engines = dw ? []
            .concat((document.getElementById('dwAhmia')||{}).checked?['ahmia']:[])
            .concat((document.getElementById('dwTorch')||{}).checked?['torch']:[])
            .concat((document.getElementById('dwDread')||{}).checked?['dread']:[])
            .concat((document.getElementById('dwDS')||{}).checked?['darksearch']:[])
            : [];
          data = JSON.stringify(o);
        }
      }catch(e){}
      return origSend.call(this, data);
    };
  });
})();
</script>

</head>
<body>

<!-- ═══ BACKGROUND ═══ -->
<canvas id="dm-canvas"></canvas>
<div class="dm-grid"></div>
<div class="dm-noise"></div>
<div class="dm-vignette"></div>
<div class="dm-scanline"></div>
<div class="sweep-line"></div>
<div class="corner tl"></div><div class="corner tr"></div>
<div class="corner bl"></div><div class="corner br"></div>

<div class="shell">

<!-- ═══ HEADER ═══ -->
<header>
  <div class="cls-banner">TOP SECRET // ALKASER NEXUS // DARK MATTER EDITION // AUTHORIZED PERSONNEL ONLY</div>

  <div class="logo-wrap">
    <div class="logo-mark"><span>◈</span></div>
    <div class="brand">
      <h1>ALKASER NEXUS</h1>
      <small>v50.0 — DARK MATTER · Intelligence Acquisition System</small>
    </div>
  </div>

  <div class="hdr-status">
    <div class="status-pill">
      <div class="s-dot" id="sDot"></div>
      <span id="sTxt">STANDBY</span>
    </div>
    <div class="utc-clock" id="utcClock">00:00:00 UTC</div>
    <div class="threat-badge green" id="threatBadge">THREAT: IDLE</div>
  </div>

  <nav class="mode-tabs">
    <button class="tab active" data-mode="username">USERNAME</button>
    <button class="tab" data-mode="phone">PHONE</button>
    <button class="tab" data-mode="email">EMAIL</button>
    <button class="tab" data-mode="domain">DOMAIN</button>
    <button class="tab" data-mode="name">NAME</button>
    <button class="tab" data-mode="deep">DEEP</button>
    <button class="tab" data-mode="image">IMG-REV</button>
  </nav>
</header>

<main>

<!-- ═══ LEFT CONTROL PANEL ═══ -->
<aside class="ctrl-panel">

  <!-- TARGET ACQUISITION -->
  <div class="sec-hdr">Target Acquisition <span class="sec-num">01</span></div>
  <div class="field" id="mainInputWrap">
    <label id="tgtLabel">Username / Handle</label>
    <input id="tgtInput" placeholder="e.g. johndoe" autocomplete="off" spellcheck="false"/>
  </div>

  <!-- MODE-SPECIFIC PANELS -->
  <div class="mode-info" id="modePhone">
    <div class="sec-hdr" style="margin-bottom:6px">Phone Capabilities</div>
    <div class="mi-item mi-ok">Telegram registration check</div>
    <div class="mi-item mi-ok">WhatsApp deeplink</div>
    <div class="mi-item mi-ok">Instagram / Facebook / TikTok</div>
    <div class="mi-item mi-ok">LinkedIn / Snapchat / Viber</div>
    <div class="mi-item mi-ok">Owner name identification</div>
    <div class="mi-item mi-ok">Carrier &amp; country detection</div>
    <div class="mi-item mi-ok">Directory scrape (10+ sites)</div>
    <div class="mi-item mi-ok">Leak database search</div>
    <div class="mi-item mi-ok">Last 7-digit strict filter</div>
  </div>

  <div class="mode-info" id="modeName">
    <div class="sec-hdr" style="margin-bottom:6px">Name Capabilities</div>
    <div class="mi-item mi-ok">All name parts must match</div>
    <div class="mi-item mi-ok">Arabic &amp; English support</div>
    <div class="mi-item mi-ok">LinkedIn / Facebook / Twitter</div>
    <div class="mi-item mi-ok">Academic &amp; professional</div>
    <div class="mi-item mi-ok">PDF &amp; document discovery</div>
    <div class="mi-item mi-ok">Relevance scoring (0–2.5)</div>
    <div class="mi-item mi-ok">15 targeted platforms</div>
    <div class="mi-item mi-ok">Zero-tolerance false positives</div>
  </div>

  <div class="mode-info" id="modeEmail">
    <div class="sec-hdr" style="margin-bottom:6px">Email Capabilities</div>
    <div class="mi-item mi-ok">Full email must appear in result</div>
    <div class="mi-item mi-ok">HIBP breach check</div>
    <div class="mi-item mi-ok">Provider domain analysis</div>
    <div class="mi-item mi-ok">Social profile linkage</div>
    <div class="mi-item mi-ok">Paste site monitoring</div>
    <div class="mi-item mi-ok">Multi-engine dork search</div>
  </div>

  <div class="img-zone" id="modeImage">
    <div class="sec-hdr">Image Source</div>
    <div class="field">
      <label>Image URL</label>
      <input id="imgUrl" placeholder="https://example.com/photo.jpg" autocomplete="off"/>
    </div>
    <div class="upload-area" id="uploadArea">
      ⬆ Click to upload image file
      <input id="uploadInput" type="file" accept="image/*" style="display:none"/>
    </div>
    <div class="upload-name" id="uploadName"></div>
    <img id="imgPreview" class="img-preview" alt="preview"/>
    <div style="font-size:8.5px;color:var(--cyan2);line-height:2;margin-top:4px">
      <div class="mi-item mi-ok">Yandex — file upload + data-bem</div>
      <div class="mi-item mi-ok">Bing — base64 upload</div>
      <div class="mi-item mi-ok">TinEye — div.match extraction</div>
      <div class="mi-item mi-ok">Google Lens — full page parse</div>
      <div class="mi-item mi-ok">SauceNAO — similarity ≥ 45%</div>
    </div>
  </div>

  <!-- PARAMETERS -->
  <div class="sec-hdr">Mission Parameters <span class="sec-num">02</span></div>
  <div class="toggle-row">
    <label class="toggle">
      <span class="toggle-label">Exact match filter</span>
      <input type="checkbox" class="hidden" id="optExact" checked>
      <div class="toggle-sw" onclick="this.previousElementSibling.click()"></div>
    </label>
    <label class="toggle">
      <span class="toggle-label">Stream to Telegram</span>
      <input type="checkbox" class="hidden" id="optTg" checked>
      <div class="toggle-sw" onclick="this.previousElementSibling.click()"></div>
    </label>
    <label class="toggle">
      <span class="toggle-label">Generate HTML report</span>
      <input type="checkbox" class="hidden" id="optReport" checked>
      <div class="toggle-sw" onclick="this.previousElementSibling.click()"></div>
    </label>
    <label class="toggle">
      <span class="toggle-label">AI intelligence report</span>
      <input type="checkbox" class="hidden" id="optAI" checked>
      <div class="toggle-sw" onclick="this.previousElementSibling.click()"></div>
    </label>
  </div>

  <!-- EXECUTE CONTROLS -->
  <div class="sec-hdr">Execute <span class="sec-num">03</span></div>
  <button class="btn-exec" id="btnStart">▶ INITIATE OPERATION</button>
  <button class="btn-stop" id="btnStop" disabled>■ ABORT MISSION</button>
  <div class="prog-track">
    <div class="prog-fill" id="progFill"></div>
  </div>
  <div class="prog-labels">
    <span id="progLabel">READY</span>
    <span class="prog-pct" id="progPct">0%</span>
  </div>

  <!-- UTILITY BUTTONS -->
  <div class="sec-hdr">Utils <span class="sec-num">04</span></div>
  <button class="btn-util" id="btnClear">✕ Clear Results</button>
  <button class="btn-util" id="btnExport">↓ Export Report</button>

  <!-- RADAR -->
  <div class="sec-hdr">Radar <span class="sec-num">05</span></div>
  <div class="radar-wrap">
    <div class="radar-ring">
      <div class="radar-h"></div>
      <div class="radar-v"></div>
      <div class="radar-sweep"></div>
      <div id="blips"></div>
    </div>
    <div class="radar-label" id="radarLabel">NO TARGETS</div>
  </div>

  <!-- STATS -->
  <div class="sec-hdr">Intelligence Stats <span class="sec-num">06</span></div>
  <div class="stat-grid">
    <div class="stat-cell"><div class="stat-val" id="kTotal">0</div><div class="stat-key">TOTAL</div></div>
    <div class="stat-cell"><div class="stat-val" id="kHits">0</div><div class="stat-key">HITS</div></div>
    <div class="stat-cell"><div class="stat-val" id="kImg">0</div><div class="stat-key">IMAGES</div></div>
    <div class="stat-cell"><div class="stat-val" id="kLeaks">0</div><div class="stat-key">LEAKS</div></div>
    <div class="stat-cell"><div class="stat-val" id="kSrc">0</div><div class="stat-key">SOURCES</div></div>
    <div class="stat-cell"><div class="stat-val" id="kTime">0s</div><div class="stat-key">ELAPSED</div></div>
  </div>

</aside>

<!-- ═══ CENTER RESULTS ═══ -->
<section class="results-panel">
  <div class="results-top">
    <input class="filter-input" id="filterInput" placeholder="filter intelligence..."/>
    <div class="chip-row" id="chipBox"></div>
    <span class="res-count" id="resCount">0 items</span>
  </div>
  <div class="cards-grid" id="resultsGrid">
    <div class="empty-state">
      <span class="empty-icon">◈</span>
      AWAITING TARGET ACQUISITION
    </div>
  </div>
</section>

<!-- ═══ RIGHT TERMINAL ═══ -->
<aside class="term-panel">
  <div class="term-hdr">
    <span>// OPERATION LOG</span>
    <div class="term-live">
      <div class="s-dot" id="termDot"></div>
      <span>LIVE</span>
    </div>
  </div>
  <div class="term-body" id="terminal">
    <div class="tl info"><span class="ts">[INIT]</span> ALKASER NEXUS v50.0 — DARK MATTER loaded</div>
    <div class="tl info"><span class="ts">[INIT]</span> All systems nominal — awaiting target</div>
  </div>
  <div class="intel-feed" id="intelFeed">
    <strong>// INTEL FEED —</strong> No active operation
  </div>
</aside>

</main>
</div>

<script>
/* ══════════════════════════════════════════
   DARK MATTER PARTICLE BACKGROUND
══════════════════════════════════════════ */
(function(){
  const c = document.getElementById('dm-canvas');
  const ctx = c.getContext('2d');
  let W = c.width = window.innerWidth;
  let H = c.height = window.innerHeight;

  const NUM = 80;
  const particles = Array.from({length: NUM}, () => ({
    x: Math.random() * W,
    y: Math.random() * H,
    vx: (Math.random() - .5) * .25,
    vy: (Math.random() - .5) * .25,
    r: Math.random() * 1.5 + .3,
    a: Math.random() * .4 + .05,
  }));

  const lines = [];

  function draw() {
    ctx.clearRect(0, 0, W, H);

    // Particles
    for (const p of particles) {
      p.x += p.vx; p.y += p.vy;
      if (p.x < 0) p.x = W;
      if (p.x > W) p.x = 0;
      if (p.y < 0) p.y = H;
      if (p.y > H) p.y = 0;

      ctx.beginPath();
      ctx.arc(p.x, p.y, p.r, 0, Math.PI * 2);
      ctx.fillStyle = `rgba(37,99,235,${p.a})`;
      ctx.fill();
    }

    // Connection lines
    for (let i = 0; i < particles.length; i++) {
      for (let j = i+1; j < particles.length; j++) {
        const dx = particles[i].x - particles[j].x;
        const dy = particles[i].y - particles[j].y;
        const d = Math.sqrt(dx*dx + dy*dy);
        if (d < 120) {
          ctx.beginPath();
          ctx.moveTo(particles[i].x, particles[i].y);
          ctx.lineTo(particles[j].x, particles[j].y);
          ctx.strokeStyle = `rgba(37,99,235,${.06 * (1 - d/120)})`;
          ctx.lineWidth = .5;
          ctx.stroke();
        }
      }
    }

    requestAnimationFrame(draw);
  }
  draw();

  window.addEventListener('resize', () => {
    W = c.width = window.innerWidth;
    H = c.height = window.innerHeight;
  });
})();

/* ══════════════════════════════════════════
   UTC CLOCK
══════════════════════════════════════════ */
function updateClock() {
  const now = new Date();
  const h = String(now.getUTCHours()).padStart(2,'0');
  const m = String(now.getUTCMinutes()).padStart(2,'0');
  const s = String(now.getUTCSeconds()).padStart(2,'0');
  document.getElementById('utcClock').textContent = `${h}:${m}:${s} UTC`;
}
setInterval(updateClock, 1000);
updateClock();

/* ══════════════════════════════════════════
   APP STATE
══════════════════════════════════════════ */
function $(id) { return document.getElementById(id); }
function esc(s) {
  return String(s).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
}

let mode = 'username';
let results = [];
let activeFilter = 'all';
let busy = false;
let ws = null;
let startTime = 0;
let timerHandle = null;
let uploadBytes = null;
let progressVal = 0;
let progIntvl = null;

const LABELS = {
  username: 'Username / Handle',
  phone:    'Phone Number (with country code)',
  email:    'Email Address',
  domain:   'Domain / URL',
  name:     'Full Name (3-4 parts)',
  deep:     'Search Target',
  image:    'Image Label (optional)',
};
const PLACEHOLDERS = {
  username: 'e.g. johndoe_x',
  phone:    'e.g. +966501234567',
  email:    'e.g. user@example.com',
  domain:   'e.g. example.com',
  name:     'e.g. محمد عبد الله السعيد',
  deep:     'e.g. John Doe',
  image:    '(optional label)',
};

/* ══════════════════════════════════════════
   MODE TABS
══════════════════════════════════════════ */
document.querySelectorAll('.tab').forEach(b => b.onclick = () => {
  if (busy) return;
  document.querySelectorAll('.tab').forEach(x => x.classList.remove('active'));
  b.classList.add('active');
  mode = b.dataset.mode;

  $('tgtLabel').textContent = LABELS[mode];
  $('tgtInput').placeholder = PLACEHOLDERS[mode];

  // Show/hide panels
  ['modePhone','modeName','modeEmail'].forEach(id => {
    const el = $(id);
    if (el) el.classList.remove('active');
  });
  const imgZone = $('modeImage');
  if (imgZone) imgZone.classList.remove('active');
  $('mainInputWrap').style.display = '';

  if (mode === 'phone') { $('modePhone').classList.add('active'); }
  else if (mode === 'name')  { $('modeName').classList.add('active'); }
  else if (mode === 'email') { $('modeEmail').classList.add('active'); }
  else if (mode === 'image') {
    imgZone.classList.add('active');
    $('mainInputWrap').style.display = 'none';
  }
});

/* ══════════════════════════════════════════
   IMAGE UPLOAD
══════════════════════════════════════════ */
$('uploadArea').onclick = () => $('uploadInput').click();
$('uploadInput').onchange = e => {
  const f = e.target.files[0];
  if (!f) return;
  $('uploadName').textContent = '▸ ' + f.name + ' (' + Math.round(f.size/1024) + ' KB)';
  const rd = new FileReader();
  rd.onload = ev => {
    uploadBytes = ev.target.result;
    const p = $('imgPreview');
    p.src = ev.target.result;
    p.style.display = 'block';
  };
  rd.readAsDataURL(f);
};

/* ══════════════════════════════════════════
   CONTROLS
══════════════════════════════════════════ */
$('btnClear').onclick = () => {
  results = [];
  renderResults();
  $('terminal').innerHTML = '';
  $('blips').innerHTML = '';
  updateStats();
  $('intelFeed').innerHTML = '<strong>// INTEL FEED —</strong> Cleared';
  $('resCount').textContent = '0 items';
};

$('btnStop').onclick = () => { if (ws) ws.close(); };
$('filterInput').oninput = renderResults;

$('btnExport').onclick = async () => {
  if (!results.length) { alert('No results to export'); return; }
  const target = $('tgtInput').value.trim() || (results[0] && results[0].title) || 'nexus';
  const r = await fetch('/api/export', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ target, mode, items: results })
  });
  const j = await r.json();
  if (j.html) {
    const a = document.createElement('a');
    a.href = '/reports/' + j.html.split('/').pop();
    a.download = j.html.split('/').pop();
    document.body.appendChild(a); a.click(); a.remove();
    tlog('ok', '📊 Report exported → ' + j.html);
  }
};

/* ══════════════════════════════════════════
   OPERATION START
══════════════════════════════════════════ */
$('btnStart').onclick = () => {
  const target = $('tgtInput').value.trim();
  if (mode !== 'image' && !target) { alert('Enter a target first.'); return; }
  if (mode === 'image' && !$('imgUrl').value.trim() && !uploadBytes) {
    alert('Provide image URL or upload file.'); return;
  }

  results = []; renderResults(); updateStats(); $('blips').innerHTML = '';
  busy = true;
  $('btnStart').disabled = true;
  $('btnStop').disabled = false;
  $('sDot').className = 's-dot scanning';
  $('termDot').className = 's-dot scanning';
  $('sTxt').textContent = 'SCANNING · ' + mode.toUpperCase();
  $('radarLabel').textContent = 'SCANNING...';
  $('threatBadge').className = 'threat-badge amber';
  $('threatBadge').textContent = 'THREAT: AMBER';
  progressVal = 0;
  $('progFill').style.width = '0%';
  $('progPct').textContent = '0%';
  $('progLabel').textContent = 'INITIALIZING...';
  startTime = Date.now();
  timerHandle = setInterval(() => {
    $('kTime').textContent = Math.round((Date.now() - startTime) / 1000) + 's';
  }, 1000);
  progIntvl = setInterval(() => {
    if (progressVal < 88) {
      progressVal += 0.35;
      $('progFill').style.width = progressVal + '%';
      $('progPct').textContent = Math.round(progressVal) + '%';
    }
  }, 600);

  const proto = location.protocol === 'https:' ? 'wss' : 'ws';
  ws = new WebSocket(`${proto}://${location.host}/ws`);

  ws.onopen = () => {
    ws.send(JSON.stringify({
      action: 'start', mode, target,
      exact:  $('optExact').checked,
      tg:     $('optTg').checked,
      report: $('optReport').checked,
      ai:     $('optAI').checked,
      image_url:  mode === 'image' ? $('imgUrl').value.trim() : '',
      image_b64:  mode === 'image' ? (uploadBytes || '') : '',
    }));
    tlog('info', '▶ OPERATION STARTED · MODE=' + mode + ' · TARGET=' + (target || $('imgUrl').value || '[IMAGE]'));
    tlog('info', '◈ Engaging ' + mode.toUpperCase() + ' intelligence protocol...');
  };

  ws.onmessage = e => {
    try {
      const m = JSON.parse(e.data);
      if (m.event === 'result') {
        results.push(m.data);
        // Auto-scroll to latest
        renderResults();
        updateStats();
        addBlip();
        addFeedItem(m.data);
        // Scroll grid to bottom for latest
        const grid = $('resultsGrid');
        if (grid) grid.scrollTop = grid.scrollHeight;
      } else if (m.event === 'log') {
        tlog(m.level || 'info', m.msg);
        $('progLabel').textContent = (m.msg || '').slice(2, 45) + '...';
      } else if (m.event === 'done') {
        onDone(m);
      }
    } catch(ex) {}
  };

  ws.onerror = () => tlog('err', 'WebSocket connection error');
  ws.onclose = () => {
    busy = false;
    $('btnStart').disabled = false;
    $('btnStop').disabled = true;
    $('sDot').className = 's-dot';
    $('termDot').className = 's-dot';
    clearInterval(progIntvl);
    clearInterval(timerHandle);
    if (!$('sTxt').textContent.startsWith('COMPLETE')) $('sTxt').textContent = 'STANDBY';
    $('threatBadge').className = 'threat-badge green';
    $('threatBadge').textContent = 'THREAT: IDLE';
  };
};

function onDone(m) {
  clearInterval(progIntvl);
  clearInterval(timerHandle);
  $('progFill').style.width = '100%';
  $('progPct').textContent = '100%';
  $('progLabel').textContent = 'OPERATION COMPLETE';
  setTimeout(() => {
    $('progFill').style.width = '0%';
    $('progPct').textContent = '0%';
  }, 4000);
  $('sTxt').textContent = 'COMPLETE · ' + results.length + ' ITEMS';
  $('radarLabel').textContent = results.length + ' ACQUIRED';
  $('threatBadge').className = 'threat-badge red';
  $('threatBadge').textContent = 'ACTIVE INTEL: ' + results.length;
  tlog('ok', '🏁 OPERATION COMPLETE · ' + results.length + ' intelligence items');
  if (m.report) tlog('ok', '📊 Report saved → ' + m.report);
  tlog('ok', '⏱ Total time: ' + Math.round((Date.now() - startTime) / 1000) + 's');
}

/* ══════════════════════════════════════════
   RADAR BLIPS
══════════════════════════════════════════ */
function addBlip() {
  const el = $('blips');
  if (el.childElementCount > 18) el.firstChild?.remove();
  const b = document.createElement('div');
  b.className = 'blip';
  const sz = Math.random() > .75 ? 7 : 5;
  b.style.width = b.style.height = sz + 'px';
  b.style.left = (12 + Math.random() * 74) + '%';
  b.style.top  = (12 + Math.random() * 74) + '%';
  el.appendChild(b);
  setTimeout(() => b.remove(), 2200);
}

/* ══════════════════════════════════════════
   INTEL FEED
══════════════════════════════════════════ */
function addFeedItem(r) {
  const src = r.source || r.platform || r.type || '?';
  const title = (r.title || r.name || '').slice(0, 55);
  const feed = $('intelFeed');
  const item = document.createElement('div');
  item.className = 'feed-item';
  item.innerHTML = `<strong>${esc(src)}</strong> — ${esc(title)}`;
  feed.innerHTML = '<strong>// INTEL FEED —</strong> Live acquisition:<br>';
  feed.appendChild(item);
}

/* ══════════════════════════════════════════
   TERMINAL LOG
══════════════════════════════════════════ */
function tlog(level, msg) {
  const term = $('terminal');
  const d = document.createElement('div');
  d.className = 'tl ' + level;
  const ts = new Date().toLocaleTimeString('en-GB', {hour12: false});
  d.innerHTML = `<span class="ts">[${ts}]</span> ${esc(msg)}`;
  term.appendChild(d);
  term.scrollTop = term.scrollHeight;
  while (term.childElementCount > 1200) term.firstChild?.remove();
}

/* ══════════════════════════════════════════
   BADGE CLASSIFICATION
══════════════════════════════════════════ */
function badgeClass(src, type) {
  const s = (src || '').toLowerCase();
  const t = (type || '').toLowerCase();
  if (t.includes('leak') || t.includes('breach') || s.includes('hibp') || s.includes('dehash')) return 'sb-leak';
  if (t.includes('archive') || s.includes('wayback') || s.includes('archive')) return 'sb-archive';
  if (t.includes('dark') || s.includes('tor') || s.includes('ahmia')) return 'sb-dark';
  if (t.includes('social') || t.includes('username') || t.includes('twitter') || t.includes('insta') || t.includes('tiktok') || t.includes('facebook')) return 'sb-social';
  if (t.includes('owner') || t.includes('phone_owner') || t.includes('name_hit')) return 'sb-owner';
  return 'sb-default';
}

/* ══════════════════════════════════════════
   RELEVANCE DOTS
══════════════════════════════════════════ */
function relDots(score) {
  if (!score) return '';
  const n = Math.round(Math.min(score, 2.5) / 2.5 * 5);
  let dots = '';
  for (let i = 0; i < 5; i++) dots += `<div class="rel-dot${i < n ? ' on' : ''}"></div>`;
  return `<div class="rel-row">REL <div class="rel-dots">${dots}</div></div>`;
}

/* ══════════════════════════════════════════
   CONFIDENCE BAR
══════════════════════════════════════════ */
function confBar(level) {
  if (!level) return '';
  const cls = {'HIGH':'cf-high','MEDIUM':'cf-med','LOW':'cf-low'}[level] || 'cf-low';
  const w   = {'HIGH':'100%','MEDIUM':'60%','LOW':'30%'}[level] || '20%';
  return `<div class="conf-bar"><div class="conf-fill ${cls}" style="width:${w}"></div></div>`;
}

/* ══════════════════════════════════════════
   BUILD CARD — STRICT 220px
══════════════════════════════════════════ */
function getSrc(r) { return r.source || r.platform || r.type || '?'; }

function buildCard(r) {
  const src   = getSrc(r);
  const type  = r.type || '';
  const title = esc(String(r.title || r.name || r.platform || '(no title)').slice(0, 140));
  const link  = esc(r.link || r.url || r.image_url || '');
  const snip  = esc(String(r.snippet || r.bio || r.description || '').slice(0, 200));
  const thumb = r.thumbnail || r.avatar || r.image_url || '';
  const bc    = badgeClass(src, type);

  // Image section (80px fixed) — only if image exists
  const imgHtml = thumb
    ? `<div class="card-img"><img src="${esc(thumb)}" loading="lazy" onerror="this.parentNode.innerHTML='<div class=card-ph>◈</div>'"></div>`
    : `<div class="card-img"><div class="card-ph">◈</div></div>`;

  // Meta info
  const meta = [];
  if (r.followers) meta.push('👥 ' + esc(String(r.followers)));
  if (r.following) meta.push('↔ ' + esc(String(r.following)));
  if (r.posts)     meta.push('📝 ' + esc(String(r.posts)));
  if (r.country)   meta.push('🌍 ' + esc(String(r.country)));
  if (r.stars)     meta.push('⭐ ' + r.stars);
  const metaHtml = meta.length ? `<div class="card-meta">${meta.join('  ')}</div>` : '';

  // Similarity badge (for reverse image)
  const simHtml = r.similarity
    ? `<div class="sim-badge">SIM ${r.similarity.toFixed(1)}%</div>`
    : '';

  const relHtml  = relDots(r.relevance_score);
  const confHtml = confBar(r.confidence);
  const linkHtml = link
    ? `<a class="card-link" href="${link}" target="_blank" rel="noopener">↗ ${link.slice(0, 70)}</a>`
    : '';

  return `<article class="card">
    <div class="card-scan"></div>
    ${imgHtml}
    <div class="card-body">
      <span class="src-badge ${bc}">${esc(src)}</span>
      <div class="card-title">${title}</div>
      ${snip ? `<div class="card-snip">${snip}</div>` : ''}
      ${metaHtml}${simHtml}${relHtml}${confHtml}${linkHtml}
    </div>
  </article>`;
}

/* ══════════════════════════════════════════
   RENDER RESULTS
══════════════════════════════════════════ */
function renderResults() {
  const grid = $('resultsGrid');
  const q = $('filterInput').value.toLowerCase();

  const filtered = results.filter(r => {
    if (activeFilter !== 'all' && getSrc(r) !== activeFilter) return false;
    return !q || JSON.stringify(r).toLowerCase().includes(q);
  });

  // Update chips
  const sources = [...new Set(results.map(getSrc))].sort();
  const cc = $('chipBox');
  cc.innerHTML = '';
  cc.appendChild(mkChip('all', 'ALL'));
  sources.slice(0, 22).forEach(s => cc.appendChild(mkChip(s, s.length > 18 ? s.slice(0, 18) + '…' : s)));

  $('resCount').textContent = filtered.length + ' / ' + results.length + ' items';

  if (!filtered.length) {
    grid.innerHTML = `<div class="empty-state">
      <span class="empty-icon">◈</span>
      ${results.length ? 'NO MATCHES FOR FILTER' : 'AWAITING TARGET ACQUISITION'}
    </div>`;
    return;
  }

  grid.innerHTML = filtered.map(buildCard).join('');
}

function mkChip(val, label) {
  const c = document.createElement('button');
  c.className = 'chip' + (activeFilter === val ? ' active' : '');
  c.textContent = label;
  c.onclick = () => { activeFilter = val; renderResults(); };
  return c;
}

/* ══════════════════════════════════════════
   UPDATE STATS
══════════════════════════════════════════ */
function updateStats() {
  $('kTotal').textContent  = results.length;
  $('kHits').textContent   = results.filter(r => /username_hit|email_provider|phone_social|twitter_profile|facebook_profile|name_hit/.test(r.type||'')).length;
  $('kImg').textContent    = results.filter(r => /(image|reverse_image)/.test(r.type||'')).length;
  $('kLeaks').textContent  = results.filter(r => /(leak|breach|email_leak|phone_leak)/.test(r.type||'')).length;
  $('kSrc').textContent    = new Set(results.map(getSrc)).size;
}
</script>
</body>
</html>"""



# ═══════════════════════════════════════════════════════════════════════════
#  ███ INTELLIGENCE PLATFORM LAYER v54 ███
#  Entity Resolution · Graph Correlation · Stylometry · Geo Intelligence
#  Behavioral Analysis · Snapshot Store · Full-Text Index · Watchlists
#  Async Crawler · Proxy Rotator · Anomaly Detection · Identity Clustering
#  ───────────────────────────────────────────────────────────────────────
#  This block is appended to the original ALKASER NEXUS engine and wires
#  itself into the orchestrator via synthesize_intel(). Telegram dispatch
#  and ngrok bootstrap remain UNCHANGED.
# ═══════════════════════════════════════════════════════════════════════════

import math, statistics, collections, itertools, difflib
from collections import Counter, defaultdict, deque

try:
    from rapidfuzz import fuzz as _rf_fuzz
    _HAS_RAPIDFUZZ = True
except Exception:
    _HAS_RAPIDFUZZ = False

try:
    import networkx as _nx
    _HAS_NX = True
except Exception:
    _HAS_NX = False


def _fuzzy_ratio(a: str, b: str) -> float:
    a = (a or "").strip().lower()
    b = (b or "").strip().lower()
    if not a or not b:
        return 0.0
    if _HAS_RAPIDFUZZ:
        return float(_rf_fuzz.token_set_ratio(a, b)) / 100.0
    return difflib.SequenceMatcher(None, a, b).ratio()


def _jaccard(a: set, b: set) -> float:
    if not a or not b:
        return 0.0
    inter = len(a & b)
    union = len(a | b)
    return inter / union if union else 0.0


def _shingles(text: str, k: int = 4) -> set:
    text = re.sub(r"\s+", " ", (text or "").lower()).strip()
    if len(text) < k:
        return {text} if text else set()
    return {text[i:i+k] for i in range(len(text) - k + 1)}


# ─── Identifier extractors ────────────────────────────────────────────────
_EMAIL_RE   = re.compile(r"[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}")
_PHONE_RE   = re.compile(r"\+?\d[\d\s().\-]{6,}\d")
_HANDLE_RE  = re.compile(r"(?<![A-Za-z0-9_])@([A-Za-z0-9_]{2,32})")
_URL_RE     = re.compile(r"https?://[^\s'\"<>]+", re.I)
_DOMAIN_RE  = re.compile(r"\b([a-z0-9\-]+\.(?:[a-z]{2,}))(?:/|\b)", re.I)
_WALLET_BTC = re.compile(r"\b(?:bc1[a-z0-9]{20,80}|[13][a-km-zA-HJ-NP-Z1-9]{25,34})\b")
_WALLET_ETH = re.compile(r"\b0x[a-fA-F0-9]{40}\b")
_IPV4_RE    = re.compile(r"\b(?:\d{1,3}\.){3}\d{1,3}\b")
_HASH_RE    = re.compile(r"\b[a-f0-9]{32,64}\b", re.I)


def _coerce_text(item: dict) -> str:
    if not isinstance(item, dict):
        return ""
    bits = []
    for k in ("title", "snippet", "bio", "name", "url", "platform", "source"):
        v = item.get(k)
        if isinstance(v, str):
            bits.append(v)
        elif isinstance(v, (list, tuple)):
            bits.extend(str(x) for x in v if x)
    return " \n ".join(bits)


def extract_identifiers(items):
    """Sweep every result and pull out entity-grade identifiers."""
    bag = {
        "emails": Counter(), "phones": Counter(), "handles": Counter(),
        "urls":   Counter(), "domains": Counter(), "wallets": Counter(),
        "ips":    Counter(), "hashes": Counter(),
    }
    for it in (items or []):
        text = _coerce_text(it)
        if not text:
            continue
        for m in _EMAIL_RE.findall(text):
            bag["emails"][m.lower()] += 1
        for m in _PHONE_RE.findall(text):
            digits = re.sub(r"\D", "", m)
            if 7 <= len(digits) <= 15:
                bag["phones"]["+" + digits if not m.strip().startswith("+") else m.strip()] += 1
        for m in _HANDLE_RE.findall(text):
            bag["handles"][m.lower()] += 1
        for m in _URL_RE.findall(text):
            bag["urls"][m] += 1
        for m in _DOMAIN_RE.findall(text):
            bag["domains"][m.lower()] += 1
        for m in _WALLET_BTC.findall(text):
            bag["wallets"]["btc:" + m] += 1
        for m in _WALLET_ETH.findall(text):
            bag["wallets"]["eth:" + m.lower()] += 1
        for m in _IPV4_RE.findall(text):
            try:
                ipaddress.ip_address(m)
                bag["ips"][m] += 1
            except Exception:
                pass
        for m in _HASH_RE.findall(text):
            bag["hashes"][m.lower()] += 1
    return {k: dict(v.most_common(50)) for k, v in bag.items()}


# ─── Entity Resolution ────────────────────────────────────────────────────
class EntityResolver:
    """Cluster the raw identifier bag into candidate identities."""

    def __init__(self, threshold: float = 0.78):
        self.threshold = threshold

    def _seed_records(self, ids: dict) -> list:
        recs = []
        for h, w in (ids.get("handles") or {}).items():
            recs.append({"key": f"handle:{h}", "handle": h, "weight": w,
                         "emails": set(), "phones": set(), "domains": set()})
        for e, w in (ids.get("emails") or {}).items():
            local, _, dom = e.partition("@")
            recs.append({"key": f"email:{e}", "handle": local.lower(), "weight": w,
                         "emails": {e}, "phones": set(), "domains": {dom}})
        for p, w in (ids.get("phones") or {}).items():
            recs.append({"key": f"phone:{p}", "handle": "", "weight": w,
                         "emails": set(), "phones": {p}, "domains": set()})
        return recs

    def _similarity(self, a: dict, b: dict) -> float:
        s = 0.0
        if a["handle"] and b["handle"]:
            s = max(s, _fuzzy_ratio(a["handle"], b["handle"]))
        if a["emails"] & b["emails"]:   s = max(s, 0.99)
        if a["phones"] & b["phones"]:   s = max(s, 0.99)
        if a["domains"] & b["domains"]: s = max(s, 0.55)
        if a["emails"] and b["handle"]:
            for e in a["emails"]:
                s = max(s, _fuzzy_ratio(e.split("@")[0], b["handle"]))
        return s

    def cluster(self, ids: dict) -> list:
        recs = self._seed_records(ids)
        n = len(recs)
        parent = list(range(n))

        def find(x):
            while parent[x] != x:
                parent[x] = parent[parent[x]]; x = parent[x]
            return x

        def union(a, b):
            ra, rb = find(a), find(b)
            if ra != rb:
                parent[rb] = ra

        for i in range(n):
            for j in range(i + 1, n):
                if self._similarity(recs[i], recs[j]) >= self.threshold:
                    union(i, j)

        groups = defaultdict(list)
        for i, r in enumerate(recs):
            groups[find(i)].append(r)

        clusters = []
        for gid, members in groups.items():
            handles = Counter()
            emails, phones, domains = set(), set(), set()
            weight = 0
            for m in members:
                if m["handle"]:
                    handles[m["handle"]] += m["weight"]
                emails  |= m["emails"]
                phones  |= m["phones"]
                domains |= m["domains"]
                weight  += m["weight"]
            if not (handles or emails or phones):
                continue
            label = (handles.most_common(1)[0][0] if handles
                     else (next(iter(emails)) if emails
                           else next(iter(phones))))
            clusters.append({
                "id": f"identity-{abs(hash(label)) % 10**8}",
                "label": label,
                "weight": weight,
                "handles": dict(handles),
                "emails":  sorted(emails),
                "phones":  sorted(phones),
                "domains": sorted(domains),
                "size":    len(members),
            })
        clusters.sort(key=lambda c: (-c["weight"], -c["size"]))
        return clusters[:50]


# ─── Graph Correlation Engine ─────────────────────────────────────────────
class GraphCorrelation:
    """Build an entity/relation graph from collected results."""

    def __init__(self):
        self.nodes = {}     # id -> {label, type, weight}
        self.edges = Counter()

    def add_node(self, nid: str, label: str, ntype: str, w: int = 1):
        if nid in self.nodes:
            self.nodes[nid]["weight"] += w
        else:
            self.nodes[nid] = {"label": label, "type": ntype, "weight": w}

    def add_edge(self, a: str, b: str, w: int = 1):
        if a == b or not a or not b:
            return
        key = tuple(sorted((a, b)))
        self.edges[key] += w

    def ingest(self, target: str, items: list, ids: dict, clusters: list):
        if target:
            self.add_node(f"target:{target}", target, "target", 5)
        for plat, hits in self._group_by_platform(items).items():
            pid = f"platform:{plat}"
            self.add_node(pid, plat, "platform", len(hits))
            if target:
                self.add_edge(f"target:{target}", pid, len(hits))
        for e, w in (ids.get("emails") or {}).items():
            nid = f"email:{e}"; self.add_node(nid, e, "email", w)
            if target: self.add_edge(f"target:{target}", nid, w)
        for p, w in (ids.get("phones") or {}).items():
            nid = f"phone:{p}"; self.add_node(nid, p, "phone", w)
            if target: self.add_edge(f"target:{target}", nid, w)
        for h, w in (ids.get("handles") or {}).items():
            nid = f"handle:{h}"; self.add_node(nid, "@" + h, "handle", w)
            if target: self.add_edge(f"target:{target}", nid, w)
        for d, w in (ids.get("domains") or {}).items():
            nid = f"domain:{d}"; self.add_node(nid, d, "domain", w)
        for cl in clusters or []:
            cid = f"identity:{cl['id']}"
            self.add_node(cid, cl["label"], "identity", cl["weight"])
            for e in cl.get("emails", []):  self.add_edge(cid, f"email:{e}")
            for p in cl.get("phones", []):  self.add_edge(cid, f"phone:{p}")
            for h in cl.get("handles", {}): self.add_edge(cid, f"handle:{h}")

    def _group_by_platform(self, items):
        g = defaultdict(list)
        for it in items or []:
            plat = (it.get("platform") or it.get("source") or "unknown")[:64]
            g[plat].append(it)
        return g

    def metrics(self) -> dict:
        if not self.nodes:
            return {"nodes": 0, "edges": 0, "density": 0.0,
                    "top_central": [], "communities": []}
        n, m = len(self.nodes), len(self.edges)
        density = (2 * m) / (n * (n - 1)) if n > 1 else 0.0
        deg = Counter()
        for (a, b), w in self.edges.items():
            deg[a] += w; deg[b] += w
        top = [{"id": nid, "label": self.nodes[nid]["label"],
                "type": self.nodes[nid]["type"], "centrality": d}
               for nid, d in deg.most_common(10)]
        comms = self._communities()
        return {"nodes": n, "edges": m, "density": round(density, 4),
                "top_central": top, "communities": comms}

    def _communities(self):
        if _HAS_NX:
            try:
                G = _nx.Graph()
                for nid, meta in self.nodes.items():
                    G.add_node(nid, **meta)
                for (a, b), w in self.edges.items():
                    G.add_edge(a, b, weight=w)
                comms = _nx.community.greedy_modularity_communities(G)
                return [
                    {"id": f"c{i}", "size": len(c),
                     "members": [self.nodes[x]["label"] for x in list(c)[:8]]}
                    for i, c in enumerate(comms[:10])
                ]
            except Exception:
                pass
        # Fallback: union-find on shared edges
        parent = {nid: nid for nid in self.nodes}
        def find(x):
            while parent[x] != x:
                parent[x] = parent[parent[x]]; x = parent[x]
            return x
        for (a, b) in self.edges:
            ra, rb = find(a), find(b)
            if ra != rb: parent[rb] = ra
        grp = defaultdict(list)
        for nid in self.nodes: grp[find(nid)].append(nid)
        out = []
        for i, members in enumerate(sorted(grp.values(), key=len, reverse=True)[:10]):
            out.append({"id": f"c{i}", "size": len(members),
                        "members": [self.nodes[m]["label"] for m in members[:8]]})
        return out

    def export(self) -> dict:
        return {
            "nodes": [{"id": nid, **meta} for nid, meta in self.nodes.items()],
            "edges": [{"source": a, "target": b, "weight": w}
                      for (a, b), w in self.edges.items()],
        }


# ─── Stylometry / Sockpuppet Detection ────────────────────────────────────
_FUNC_WORDS = set("""
the of and to a in is it you that he was for on are with as i his they be at
one have this from or had by not word but what some we can out other were all
there when up use your how said an each she which do their time if will way
about many then them write would like so these her long make thing see him two
has look more day could go come did number sound no most people my over know
water than call first who may down side been now find any new work part take
get place made live where after back little only round man year came show every
في من على إلى عن أن لا ما هذا هذه ذلك تلك هو هي هم هن نحن أنت أنتم كان كانت
كانوا قد لقد بل أو ثم حتى لكن إذا إنما لأن لأنه كذلك أيضا فقط جدا كل بعض
""".split())


class Stylometry:
    """Compute a writing-style fingerprint per author."""

    @staticmethod
    def fingerprint(text: str) -> dict:
        text = (text or "").strip()
        if not text:
            return {}
        words = re.findall(r"[\w']+", text.lower(), re.UNICODE)
        sents = re.split(r"[.!?؟،]+", text)
        sents = [s for s in sents if s.strip()]
        wc = len(words); uc = len(set(words))
        ttr = (uc / wc) if wc else 0.0
        avg_wlen = (sum(len(w) for w in words) / wc) if wc else 0.0
        avg_slen = (wc / len(sents)) if sents else 0.0
        fw = Counter(w for w in words if w in _FUNC_WORDS)
        trigs = Counter(text[i:i+3] for i in range(len(text) - 2))
        punct = Counter(c for c in text if c in ".,;:!?؟،—-()[]{}\"'")
        return {
            "words": wc, "unique": uc, "ttr": round(ttr, 4),
            "avg_word_len": round(avg_wlen, 2),
            "avg_sent_len": round(avg_slen, 2),
            "top_func_words": dict(fw.most_common(15)),
            "top_trigrams":   dict(trigs.most_common(15)),
            "punct":          dict(punct.most_common(10)),
        }

    @staticmethod
    def similarity(a: dict, b: dict) -> float:
        if not a or not b:
            return 0.0
        ka = set(a.get("top_func_words", {}).keys())
        kb = set(b.get("top_func_words", {}).keys())
        ta = set(a.get("top_trigrams", {}).keys())
        tb = set(b.get("top_trigrams", {}).keys())
        s_fw = _jaccard(ka, kb)
        s_tg = _jaccard(ta, tb)
        s_ttr = 1.0 - min(1.0, abs(a.get("ttr", 0) - b.get("ttr", 0)))
        s_wl  = 1.0 - min(1.0, abs(a.get("avg_word_len", 0) - b.get("avg_word_len", 0)) / 5.0)
        return round(0.40 * s_fw + 0.35 * s_tg + 0.15 * s_ttr + 0.10 * s_wl, 4)

    @staticmethod
    def sockpuppet_candidates(items, min_words: int = 40, threshold: float = 0.62):
        prints = []
        for it in items or []:
            handle = (it.get("platform") or it.get("source") or "") + ":" + (
                it.get("title") or "")[:40]
            text = _coerce_text(it)
            if len(re.findall(r"\w+", text)) < min_words:
                continue
            fp = Stylometry.fingerprint(text)
            if fp: prints.append((handle, fp))
        cands = []
        for i in range(len(prints)):
            for j in range(i + 1, len(prints)):
                sim = Stylometry.similarity(prints[i][1], prints[j][1])
                if sim >= threshold:
                    cands.append({"a": prints[i][0], "b": prints[j][0],
                                  "similarity": sim})
        cands.sort(key=lambda x: -x["similarity"])
        return cands[:30]


# ─── Geo Intelligence ─────────────────────────────────────────────────────
_COUNTRY_HINTS = {
    "🇸🇦 Saudi Arabia": ["saudi", "riyadh", "jeddah", "السعودية", "الرياض", "جدة", "مكة"],
    "🇪🇬 Egypt":        ["egypt", "cairo", "alexandria", "مصر", "القاهرة", "الإسكندرية"],
    "🇦🇪 UAE":          ["uae", "dubai", "abu dhabi", "الإمارات", "دبي", "أبوظبي"],
    "🇮🇶 Iraq":         ["iraq", "baghdad", "العراق", "بغداد", "الموصل", "البصرة"],
    "🇯🇴 Jordan":       ["jordan", "amman", "الأردن", "عمان"],
    "🇰🇼 Kuwait":       ["kuwait", "الكويت"],
    "🇶🇦 Qatar":        ["qatar", "doha", "قطر", "الدوحة"],
    "🇾🇪 Yemen":        ["yemen", "sanaa", "اليمن", "صنعاء", "عدن"],
    "🇸🇾 Syria":        ["syria", "damascus", "سوريا", "دمشق", "حلب"],
    "🇱🇧 Lebanon":      ["lebanon", "beirut", "لبنان", "بيروت"],
    "🇲🇦 Morocco":      ["morocco", "rabat", "casablanca", "المغرب", "الرباط", "الدار البيضاء"],
    "🇩🇿 Algeria":      ["algeria", "algiers", "الجزائر"],
    "🇹🇳 Tunisia":      ["tunisia", "tunis", "تونس"],
    "🇱🇾 Libya":        ["libya", "tripoli", "ليبيا", "طرابلس"],
    "🇺🇸 USA":          ["united states", "usa", "new york", "california", "texas"],
    "🇬🇧 UK":           ["united kingdom", "london", "england", "britain"],
    "🇫🇷 France":       ["france", "paris", "فرنسا", "باريس"],
    "🇩🇪 Germany":      ["germany", "berlin", "deutschland", "ألمانيا"],
    "🇹🇷 Türkiye":      ["turkey", "istanbul", "ankara", "تركيا", "اسطنبول"],
    "🇮🇷 Iran":         ["iran", "tehran", "إيران", "طهران"],
    "🇷🇺 Russia":       ["russia", "moscow", "روسيا", "موسكو"],
}


class GeoIntel:
    @staticmethod
    def analyze(items) -> dict:
        bag = Counter(); city_bag = Counter()
        for it in items or []:
            text = _coerce_text(it).lower()
            for country, kws in _COUNTRY_HINTS.items():
                for k in kws:
                    if k.lower() in text:
                        bag[country] += 1
                        city_bag[k.title()] += 1
                        break
        return {
            "countries": dict(bag.most_common(20)),
            "cities":    dict(city_bag.most_common(30)),
            "primary":   (bag.most_common(1)[0] if bag else ("Unknown", 0)),
        }


# ─── Behavioral Correlator ────────────────────────────────────────────────
_TIME_RE = re.compile(
    r"(20\d{2})[-/](0[1-9]|1[0-2])[-/](0[1-9]|[12]\d|3[01])"
    r"(?:[T ](\d{2}):(\d{2}))?"
)


class BehaviorCorrelator:
    @staticmethod
    def analyze(items) -> dict:
        hours = Counter(); days = Counter(); years = Counter()
        events = 0
        for it in items or []:
            for m in _TIME_RE.finditer(_coerce_text(it)):
                y, mo, d, hh, mm = m.groups()
                years[y] += 1
                try:
                    from datetime import date
                    days[date(int(y), int(mo), int(d)).strftime("%a")] += 1
                except Exception:
                    pass
                if hh:
                    hours[int(hh)] += 1
                events += 1
        peak_hour = (hours.most_common(1)[0] if hours else (None, 0))
        return {
            "events":     events,
            "by_year":    dict(sorted(years.items())),
            "by_weekday": dict(days.most_common(7)),
            "by_hour":    {str(h): hours[h] for h in range(24) if hours[h]},
            "peak_hour_utc": peak_hour[0],
        }


# ─── Snapshot Store ───────────────────────────────────────────────────────
SNAPSHOT_DIR = Path.home() / ".alkaser" / "snapshots"
try:
    SNAPSHOT_DIR.mkdir(parents=True, exist_ok=True)
except Exception:
    SNAPSHOT_DIR = Path(tempfile.gettempdir()) / "alkaser_snapshots"
    SNAPSHOT_DIR.mkdir(parents=True, exist_ok=True)


class SnapshotStore:
    @staticmethod
    def save(target: str, mode: str, items: list, synth: dict) -> Path:
        slug = re.sub(r"[^A-Za-z0-9_\-]+", "_", (target or "anonymous"))[:60]
        ts = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
        p = SNAPSHOT_DIR / f"{slug}__{mode}__{ts}.json"
        try:
            p.write_text(json.dumps({
                "target": target, "mode": mode, "ts": ts,
                "count": len(items or []),
                "items": items, "intel": synth,
            }, ensure_ascii=False), encoding="utf-8")
        except Exception:
            pass
        return p

    @staticmethod
    def list_for(target: str) -> list:
        if not target:
            return []
        slug = re.sub(r"[^A-Za-z0-9_\-]+", "_", target)[:60]
        return sorted(SNAPSHOT_DIR.glob(f"{slug}__*.json"))


# ─── Full-Text TF-IDF Index ───────────────────────────────────────────────
_TOK_RE = re.compile(r"[\w']+", re.UNICODE)


class IndexStore:
    def __init__(self):
        self.docs = []         # list of dicts
        self.tf   = []         # list of Counter
        self.df   = Counter()  # token -> doc-count

    def add(self, item: dict):
        text = _coerce_text(item)
        toks = [t.lower() for t in _TOK_RE.findall(text) if len(t) > 1]
        if not toks: return
        c = Counter(toks)
        self.docs.append(item); self.tf.append(c)
        for t in set(toks): self.df[t] += 1

    def add_many(self, items):
        for it in items or []:
            self.add(it)

    def _idf(self, t):
        n = len(self.docs)
        df = self.df.get(t, 0)
        return math.log((n + 1) / (df + 1)) + 1.0

    def search(self, query: str, k: int = 20):
        qtoks = [t.lower() for t in _TOK_RE.findall(query or "") if len(t) > 1]
        if not qtoks or not self.docs: return []
        scores = []
        for i, c in enumerate(self.tf):
            s = sum(c.get(t, 0) * self._idf(t) for t in qtoks)
            if s > 0: scores.append((s, i))
        scores.sort(reverse=True)
        return [{"score": round(s, 4), **self.docs[i]} for s, i in scores[:k]]


# ─── Watchlist Engine ─────────────────────────────────────────────────────
class WatchlistEngine:
    DEFAULT_KEYWORDS = ["leak", "breach", "dump", "password", "credit card",
                        "تسريب", "اختراق", "كلمة المرور"]

    def __init__(self, keywords=None):
        self.keywords = [k.strip().lower() for k in (keywords or [])
                         if k and k.strip()]
        if not self.keywords:
            self.keywords = list(self.DEFAULT_KEYWORDS)

    def scan(self, items):
        alerts = []
        for it in items or []:
            text = _coerce_text(it).lower()
            hits = [k for k in self.keywords if k in text]
            if hits:
                alerts.append({
                    "severity": "high" if len(hits) >= 2 else "medium",
                    "keywords": hits,
                    "platform": it.get("platform") or it.get("source"),
                    "title":    it.get("title", "")[:160],
                    "url":      it.get("url", ""),
                })
        return alerts


# ─── Async Crawler (light wrapper, optional) ──────────────────────────────
class AsyncCrawler:
    """Asyncio + aiohttp wrapper. Falls back to threadpool when aiohttp absent."""

    def __init__(self, concurrency: int = 16, timeout: int = 12):
        self.concurrency = concurrency; self.timeout = timeout

    def fetch_many(self, urls):
        try:
            import aiohttp
        except Exception:
            return self._fallback(urls)

        async def runner():
            results = []
            sem = asyncio.Semaphore(self.concurrency)
            timeout = aiohttp.ClientTimeout(total=self.timeout)
            async with aiohttp.ClientSession(timeout=timeout, headers=_headers()) as s:
                async def one(u):
                    async with sem:
                        try:
                            async with s.get(u) as r:
                                t = await r.text(errors="ignore")
                                results.append({"url": u, "status": r.status,
                                                "text": t[:200000]})
                        except Exception as e:
                            results.append({"url": u, "status": 0,
                                            "error": str(e)[:200]})
                await asyncio.gather(*(one(u) for u in urls))
            return results

        try:
            return asyncio.run(runner())
        except RuntimeError:
            return self._fallback(urls)

    def _fallback(self, urls):
        out = []
        def one(u):
            try:
                r = safe_get(u, timeout=self.timeout)
                out.append({"url": u, "status": r.status_code if r else 0,
                            "text": (r.text[:200000] if r is not None else "")})
            except Exception as e:
                out.append({"url": u, "status": 0, "error": str(e)[:200]})
        with ThreadPoolExecutor(max_workers=self.concurrency) as pool:
            list(pool.map(one, urls))
        return out


# ─── Proxy Rotator ────────────────────────────────────────────────────────
class ProxyRotator:
    def __init__(self, proxies=None):
        env = os.environ.get("ALKASER_PROXIES", "")
        pool = list(proxies or [])
        if env:
            pool.extend([p.strip() for p in env.split(",") if p.strip()])
        self.pool = pool
        self._cycle = itertools.cycle(self.pool) if self.pool else None
        self._lock = threading.Lock()

    def next(self):
        if not self._cycle: return None
        with self._lock:
            p = next(self._cycle)
        return {"http": p, "https": p}

    def __bool__(self):
        return bool(self.pool)


# ─── Anomaly Detection ────────────────────────────────────────────────────
class AnomalyDetector:
    @staticmethod
    def detect(items, ids: dict, clusters: list) -> list:
        flags = []
        # Multiple emails on one handle
        for cl in clusters or []:
            if len(cl.get("emails", [])) >= 3:
                flags.append({"kind": "many_emails", "identity": cl["label"],
                              "count": len(cl["emails"]),
                              "detail": "Identity bound to ≥3 distinct emails"})
            if len(cl.get("phones", [])) >= 2:
                flags.append({"kind": "many_phones", "identity": cl["label"],
                              "count": len(cl["phones"]),
                              "detail": "Identity bound to ≥2 distinct phones"})
        # Cross-platform handle reuse
        handles = ids.get("handles") or {}
        for h, w in handles.items():
            if w >= 5:
                flags.append({"kind": "handle_reuse", "handle": h, "count": w,
                              "detail": "Handle appears across many platforms"})
        # Leak keyword density
        leak_hits = sum(1 for it in items or []
                        if re.search(r"(leak|breach|dump|تسريب|اختراق)",
                                     _coerce_text(it), re.I))
        if leak_hits >= 3:
            flags.append({"kind": "leak_density", "count": leak_hits,
                          "detail": "Multiple leak/breach indicators detected"})
        return flags[:50]


# ─── Confidence Scoring ───────────────────────────────────────────────────
class ConfidenceScorer:
    @staticmethod
    def score(target: str, mode: str, items: list, ids: dict, clusters: list) -> dict:
        n = len(items or [])
        base = min(0.45, n / 200.0)
        diversity = len({(it.get("source") or it.get("platform"))
                         for it in items or []}) / 30.0
        identity_boost = 0.20 if clusters else 0.0
        id_boost = min(0.20, len(ids.get("emails", {})) * 0.02
                              + len(ids.get("phones", {})) * 0.03
                              + len(ids.get("handles", {})) * 0.01)
        score = round(min(1.0, base + diversity + identity_boost + id_boost), 3)
        verdict = ("HIGH" if score >= 0.75 else
                   "MEDIUM" if score >= 0.45 else
                   "LOW")
        return {"score": score, "verdict": verdict, "items": n,
                "diversity": round(diversity, 3)}


# ─── Global Index Store (persists for life of process) ────────────────────
GLOBAL_INDEX = IndexStore()
SESSION_GRAPH_LAST = {}


def synthesize_intel(target: str, mode: str, items: list,
                     watch_keywords=None, emit=None) -> dict:
    """Run the full intelligence pipeline over collected items.

    Returns a dict with: identifiers, clusters, graph, stylometry,
    geo, behavior, watchlist, anomalies, confidence, snapshot.
    """
    def _emit(channel, payload):
        if emit:
            try: emit(channel, payload)
            except Exception: pass

    _emit("log", "🧠 [INTEL] Extracting identifiers …")
    ids = extract_identifiers(items)

    _emit("log", "🧠 [INTEL] Resolving identities …")
    clusters = EntityResolver().cluster(ids)

    _emit("log", "🧠 [INTEL] Building entity graph …")
    g = GraphCorrelation()
    g.ingest(target, items, ids, clusters)
    metrics = g.metrics()

    _emit("log", "🧠 [INTEL] Stylometric scan …")
    socks = Stylometry.sockpuppet_candidates(items)

    _emit("log", "🧠 [INTEL] Geo-distribution …")
    geo = GeoIntel.analyze(items)

    _emit("log", "🧠 [INTEL] Behavioral patterns …")
    beh = BehaviorCorrelator.analyze(items)

    _emit("log", "🧠 [INTEL] Watchlist sweep …")
    alerts = WatchlistEngine(watch_keywords).scan(items)

    _emit("log", "🧠 [INTEL] Anomaly detection …")
    anomalies = AnomalyDetector.detect(items, ids, clusters)

    conf = ConfidenceScorer.score(target, mode, items, ids, clusters)

    try:
        GLOBAL_INDEX.add_many(items)
    except Exception:
        pass

    synth = {
        "identifiers": ids,
        "clusters":    clusters,
        "graph":       {"metrics": metrics, "export": g.export()},
        "stylometry":  socks,
        "geo":         geo,
        "behavior":    beh,
        "watchlist":   alerts,
        "anomalies":   anomalies,
        "confidence":  conf,
        "ts":          datetime.now(timezone.utc).isoformat(),
    }

    try:
        snap = SnapshotStore.save(target, mode, items, synth)
        synth["snapshot"] = str(snap)
    except Exception:
        synth["snapshot"] = None

    SESSION_GRAPH_LAST.clear()
    SESSION_GRAPH_LAST.update(synth)
    return synth


def render_intel_html(synth: dict) -> str:
    """Render the synthesized intel as an HTML block to embed in the report."""
    if not synth: return ""
    conf  = synth.get("confidence", {})
    geo   = synth.get("geo", {})
    beh   = synth.get("behavior", {})
    gm    = synth.get("graph", {}).get("metrics", {})
    cls   = synth.get("clusters", [])
    socks = synth.get("stylometry", [])
    alerts= synth.get("watchlist", [])
    anom  = synth.get("anomalies", [])

    def _row(k, v):
        return f'<tr><td style="padding:6px 10px;color:#9be8b5;border-bottom:1px solid #163;">{html.escape(str(k))}</td><td style="padding:6px 10px;color:#dfffe4;border-bottom:1px solid #163;">{html.escape(str(v))}</td></tr>'

    parts = ['<div style="margin-top:30px;padding:18px 22px;background:#04140a;border:1px solid #00ff66;border-radius:14px;box-shadow:0 0 24px #00ff6633;">']
    parts.append('<h2 style="color:#00ff66;text-shadow:0 0 8px #00ff66;">🧠 INTELLIGENCE SYNTHESIS</h2>')

    parts.append('<h3 style="color:#9be8b5;">Confidence</h3><table style="width:100%;border-collapse:collapse;">')
    parts.append(_row("Verdict",   conf.get("verdict")))
    parts.append(_row("Score",     conf.get("score")))
    parts.append(_row("Items",     conf.get("items")))
    parts.append(_row("Diversity", conf.get("diversity")))
    parts.append("</table>")

    parts.append('<h3 style="color:#9be8b5;margin-top:18px;">Graph</h3><table style="width:100%;border-collapse:collapse;">')
    parts.append(_row("Nodes",      gm.get("nodes")))
    parts.append(_row("Edges",      gm.get("edges")))
    parts.append(_row("Density",    gm.get("density")))
    parts.append(_row("Communities", len(gm.get("communities", []))))
    parts.append("</table>")

    if cls:
        parts.append('<h3 style="color:#9be8b5;margin-top:18px;">Identity Clusters</h3><ul>')
        for c in cls[:12]:
            parts.append(f'<li style="color:#dfffe4;">⟁ <b>{html.escape(c["label"])}</b> '
                         f'— handles:{len(c["handles"])}, emails:{len(c["emails"])}, '
                         f'phones:{len(c["phones"])}, weight:{c["weight"]}</li>')
        parts.append("</ul>")

    if geo.get("countries"):
        parts.append('<h3 style="color:#9be8b5;margin-top:18px;">Geo-distribution</h3><table style="width:100%;border-collapse:collapse;">')
        for k, v in list(geo["countries"].items())[:10]:
            parts.append(_row(k, v))
        parts.append("</table>")

    if beh.get("events"):
        parts.append('<h3 style="color:#9be8b5;margin-top:18px;">Behavior</h3><table style="width:100%;border-collapse:collapse;">')
        parts.append(_row("Events with timestamps", beh.get("events")))
        parts.append(_row("Peak hour UTC",          beh.get("peak_hour_utc")))
        parts.append("</table>")

    if alerts:
        parts.append('<h3 style="color:#ff5555;margin-top:18px;">Watchlist Alerts</h3><ul>')
        for a in alerts[:20]:
            parts.append(f'<li style="color:#ffb3b3;">[{a["severity"].upper()}] '
                         f'{html.escape(", ".join(a["keywords"]))} — '
                         f'{html.escape(a.get("title") or "")}</li>')
        parts.append("</ul>")

    if anom:
        parts.append('<h3 style="color:#ffdd55;margin-top:18px;">Anomalies</h3><ul>')
        for a in anom[:20]:
            parts.append(f'<li style="color:#ffeebb;">• {html.escape(a["kind"])} — '
                         f'{html.escape(a.get("detail",""))}</li>')
        parts.append("</ul>")

    if socks:
        parts.append('<h3 style="color:#9be8b5;margin-top:18px;">Possible Sockpuppets</h3><ul>')
        for s in socks[:12]:
            parts.append(f'<li style="color:#dfffe4;">↔ {html.escape(s["a"])} ≈ '
                         f'{html.escape(s["b"])} (sim={s["similarity"]})</li>')
        parts.append("</ul>")

    parts.append("</div>")
    return "".join(parts)


# Monkey-patch build_html_report so the synthesis block is appended whenever
# SESSION_GRAPH_LAST is populated. We keep the original signature intact.
_original_build_html_report = build_html_report


def build_html_report(target, mode, items):
    html_doc = _original_build_html_report(target, mode, items)
    try:
        if SESSION_GRAPH_LAST:
            block = render_intel_html(SESSION_GRAPH_LAST)
            if block and "</body>" in html_doc:
                html_doc = html_doc.replace("</body>", block + "</body>", 1)
    except Exception:
        pass
    return html_doc


# ─── New API endpoints ────────────────────────────────────────────────────
@app.get("/api/intel")
def api_intel():
    return SESSION_GRAPH_LAST or {"status": "no synthesis yet"}


@app.get("/api/snapshots")
def api_snapshots(target: str = ""):
    return {"snapshots": [str(p.name) for p in SnapshotStore.list_for(target)]}


@app.get("/api/search")
def api_search(q: str = "", k: int = 20):
    return {"query": q, "results": GLOBAL_INDEX.search(q, k=k)}


@app.get("/api/graph")
def api_graph():
    return SESSION_GRAPH_LAST.get("graph", {"metrics": {}, "export": {"nodes": [], "edges": []}})


# ═══════════════════════════════════════════════════════════════════════
#  ROUTES
# ═══════════════════════════════════════════════════════════════════════
@app.get("/", response_class=HTMLResponse)
def index():
    return HTMLResponse(INDEX_HTML)


@app.get("/api/health")
def health():
    return {
        "app": APP_NAME,
        "version": APP_VERSION,
        "ddg": DUCKDUCKGO_AVAILABLE,
        "port": PORT,
        "platforms": len(SOCIAL_PLATFORMS),
        "status": "operational",
    }


@app.get("/reports/{name}")
def fetch_report(name: str):
    p = REPORTS_DIR / name
    if not p.exists() or ".." in name:
        return JSONResponse({"error": "not found"}, status_code=404)
    return FileResponse(p)


@app.post("/api/export")
async def export_report(payload: dict):
    target = (payload.get("target") or "results")[:80]
    mode   = (payload.get("mode") or "deep")[:30]
    items  = payload.get("items") or []
    h, j   = save_report(target, mode, items)
    if TELEGRAM_ENABLED:
        try:
            tg_doc(h.read_bytes(), h.name, f"📊 {mode} — {target} ({len(items)} items)")
        except Exception:
            pass
    return {"html": str(h), "json": str(j)}


# ═══════════════════════════════════════════════════════════════════════
#  WEBSOCKET — MAIN OPERATION ENGINE
# ═══════════════════════════════════════════════════════════════════════
@app.websocket("/ws")
async def ws_endpoint(ws: WebSocket):
    await ws.accept()
    loop = asyncio.get_event_loop()
    stop_flag = threading.Event()

    async def send(event, payload):
        try:
            await ws.send_text(json.dumps({"event": event, **payload}))
        except Exception:
            pass

    def emit(event, data):
        if stop_flag.is_set():
            return

        if event == "log":
            asyncio.run_coroutine_threadsafe(send("log", {"msg": str(data), "level": "info"}), loop)
        elif event == "result":
            asyncio.run_coroutine_threadsafe(send("result", {"data": data}), loop)
        elif event == "done":
            asyncio.run_coroutine_threadsafe(send("done", data if isinstance(data, dict) else {"total": 0}), loop)

        # Telegram streaming (UNCHANGED mechanism)
        if not TELEGRAM_ENABLED:
            return
        try:
            if event == "log":
                tg_text(f"📟 {data}")
            elif event == "result" and isinstance(data, dict):
                rtype = data.get("type", "")
                src = data.get("source") or data.get("platform") or "?"
                title = data.get("title") or data.get("name") or data.get("platform") or "No Title"
                link = data.get("link") or data.get("url") or data.get("image_url") or ""
                snippet = data.get("snippet") or data.get("bio") or data.get("description") or ""
                image_url = data.get("image_url") or data.get("thumbnail") or data.get("avatar") or ""
                confidence = data.get("confidence", "")

                if rtype == "username_hit":
                    msg = f"🕵️ <b>{html.escape(str(src))}</b>\n👤 {html.escape(str(title))}\n"
                    if data.get("followers"): msg += f"👥 {data['followers']}\n"
                    if data.get("posts"):     msg += f"📝 {data['posts']}\n"
                    if data.get("bio"):       msg += f"💬 {html.escape(str(data['bio'])[:200])}\n"
                    if link:                  msg += f"🔗 {html.escape(str(link))}"
                elif rtype in ("phone_social", "phone_owner", "phone_info"):
                    msg = f"📱 <b>{html.escape(str(src))}</b>\n📞 {html.escape(str(title))}\n"
                    if snippet:          msg += f"ℹ️ {html.escape(str(snippet)[:200])}\n"
                    if data.get("country"): msg += f"🌍 {data['country']}\n"
                    if confidence:       msg += f"✓ {confidence}\n"
                    if link:             msg += f"🔗 {html.escape(str(link))}"
                elif rtype in ("leak", "email_leak"):
                    msg = f"🔥 <b>LEAK · {html.escape(str(src))}</b>\n⚠️ {html.escape(str(title)[:200])}\n"
                    if snippet: msg += f"📝 {html.escape(str(snippet)[:300])}"
                elif rtype == "name_hit":
                    score = data.get("relevance_score", 0)
                    msg = f"🪪 <b>NAME HIT · {html.escape(str(src))}</b>\n👤 {html.escape(str(title))}\n"
                    if score:   msg += f"🎯 Relevance: {score:.2f}\n"
                    if snippet: msg += f"📝 {html.escape(str(snippet)[:200])}\n"
                    if link:    msg += f"🔗 {html.escape(str(link))}"
                elif rtype == "reverse_image":
                    similarity = data.get("similarity", "")
                    msg = f"🖼️ <b>REVERSE IMAGE · {html.escape(str(src))}</b>\n📌 {html.escape(str(title)[:200])}\n"
                    if similarity: msg += f"🎯 Similarity: {similarity:.1f}%\n"
                    if link:       msg += f"🔗 {html.escape(str(link))}"
                else:
                    msg = f"📊 <b>{html.escape(str(src))}</b>\n📌 {html.escape(str(title)[:200])}\n"
                    if snippet: msg += f"📝 {html.escape(str(snippet)[:300])}\n"
                    if link:    msg += f"🔗 {html.escape(str(link))}"

                tg_text(msg.strip())

                if rtype in ("image", "reverse_image") and image_url:
                    tg_photo(image_url, caption=title[:200])
                elif image_url and rtype in ("username_hit", "twitter_profile", "facebook_profile"):
                    tg_photo(image_url, caption=f"{src}: {title[:200]}")

            elif event == "done":
                total = data.get("total", 0) if isinstance(data, dict) else 0
                tg_text(
                    f"🏁 <b>ALKASER OSINT v51 — Operation Complete</b>\n"
                    f"📊 {total} intelligence items collected\n"
                    f"⏱ Report generated successfully"
                )
        except Exception as e:
            logging.debug(f"emit tg: {e}")

    try:
        msg = await ws.receive_text()
        cfg = json.loads(msg)
        if cfg.get("action") != "start":
            await ws.close()
            return

        mode      = cfg.get("mode", "username")
        target    = (cfg.get("target") or "").strip()
        exact     = bool(cfg.get("exact", True))
        do_tg     = bool(cfg.get("tg", True))
        do_rep    = bool(cfg.get("report", True))
        do_ai     = bool(cfg.get("ai", True))
        image_url = cfg.get("image_url", "").strip()
        image_b64 = cfg.get("image_b64", "")
        use_archive  = bool(cfg.get("use_archive", True))
        archive_only = bool(cfg.get("archive_only", False))
        deep_files   = bool(cfg.get("deep_files", False))
        use_tor      = bool(cfg.get("use_tor", False))
        dw_engines   = cfg.get("dw_engines") or ["ahmia", "torch", "dread", "darksearch"]

        global TELEGRAM_ENABLED
        TELEGRAM_ENABLED = do_tg

        emit("log", f"▶ mode={mode} | target={target or image_url} | exact={exact} | tg={do_tg}")
        if do_tg:
            tg_text(
                f"🚀 <b>ALKASER OSINT v51 — MILITARY INTEL</b>\n"
                f"🎯 Mode: <b>{mode.upper()}</b>\n"
                f"🔍 Target: <code>{html.escape(target or image_url)}</code>\n"
                f"⚡ Operation initiated..."
            )

        items = []

        def runner():
            try:
                if mode == "username":
                    items.extend(username_scan(target, emit))

                elif mode == "phone":
                    res = PhoneOSINT.full_scan(target, emit)
                    items.append({
                        "type": "phone_summary",
                        "source": "Phone Summary",
                        "platform": "📱 Phone Intelligence",
                        "title": f"Phone OSINT: {target}",
                        "snippet": (
                            f"Country: {res.get('country', '?')} | "
                            f"Social: {len(res.get('social_accounts', []))} | "
                            f"Web mentions: {len(res.get('web_mentions', []))} | "
                            f"Directories: {len(res.get('mentions', []))}"
                        ),
                        "country": res.get("country", ""),
                        "variants": res.get("variants", []),
                    })
                    for k in ("social_accounts", "mentions", "web_mentions", "directories", "providers"):
                        for r in res.get(k, []):
                            if r not in items:
                                items.append(r)

                elif mode == "email":
                    res = email_recon(target, emit)
                    for k in ("providers", "leaks", "mentions"):
                        for r in res.get(k, []):
                            if r not in items:
                                items.append(r)

                elif mode == "domain":
                    items.extend(domain_recon(target, emit))

                elif mode == "name":
                    items.extend(NameIntelligence.full_name_scan(target, emit))

                elif mode == "image":
                    img_bytes = None
                    image_url = cfg.get("image_url", "").strip()
                    image_b64 = cfg.get("image_b64", "")
                    if image_b64:
                        try:
                            b = image_b64.split(",", 1)[-1]
                            img_bytes = base64.b64decode(b)
                        except Exception:
                            img_bytes = None

                    # إذا وُجدت بيانات صورة ولم يكن هناك رابط، ارفعها إلى imgbb
                    if img_bytes and not image_url:
                        uploaded_url = upload_to_imgbb(img_bytes)
                        if uploaded_url:
                            image_url = uploaded_url
                            emit("log", f"📤 Uploaded to imgbb: {image_url}")
                            img_bytes = None  # نعتمد على الرابط
                        else:
                            emit("log", "⚠️ imgbb upload failed; some engines may not work")

                    items.extend(reverse_image_all(image_url or None, img_bytes, emit))

                elif mode == "darkweb":
                    emit("log", f"🕸️ DARK WEB scan: {target}  (Tor={use_tor})")
                    dw = DarkWebSearch.search(target, max_results=40,
                                              use_tor=use_tor, engines=dw_engines,
                                              on_event=emit)
                    if exact:
                        dw = apply_relevance_filter(dw, target, "deep")
                    items.extend(dw)
                    # Also include Ahmia via existing class for redundancy
                    for r in Ahmia.search(target, max_results=15):
                        items.append(r); emit("result", r)

                else:  # deep
                    ds = DeepSearch(emit, exact_match=exact, target=target,
                                    use_archive=use_archive,
                                    archive_only=archive_only,
                                    deep_files=deep_files,
                                    use_tor=use_tor)
                    ds.run(target)
                    items.extend(ds.results)
                    emit("log", "📥 Extra social scrape...")
                    with ThreadPoolExecutor(max_workers=6) as ex:
                        futs = {
                            ex.submit(fetch_reddit_posts, target, 8):    "Reddit",
                            ex.submit(fetch_youtube_channels, target, 5):"YouTube",
                            ex.submit(fetch_youtube_videos, target, 5):  "YT Videos",
                            ex.submit(fetch_github_repos, target):       "GitHub",
                            ex.submit(fetch_nitter_profile, target):     "Twitter",
                        }
                        for f in as_completed(futs):
                            try:
                                for r in (f.result() or []):
                                    items.append(r)
                                    emit("result", r)
                            except Exception:
                                pass
                    for r in WaybackMachine.search_text(target, 20):
                        items.append(r)
                        emit("result", r)
                    for r in ArchiveOrg.search(target, 15):
                        items.append(r)
                        emit("result", r)

            except Exception as e:
                emit("log", f"❌ Runner error: {e}")
                logging.exception("runner")
            finally:
                report_path = ""
                if do_ai and items and len(items) >= 2:
                    emit("log", "🧠 Generating AI intelligence report...")
                    ai_text = ai_analysis(target or image_url, mode, items)
                    if ai_text:
                        ai_item = {
                            "type": "ai_report",
                            "source": "AI Analysis",
                            "platform": "🧠 AI Intelligence",
                            "title": f"AI Report: {target or image_url}",
                            "snippet": ai_text[:500],
                            "bio": ai_text,
                        }
                        items.append(ai_item)
                        emit("result", ai_item)

# ── INTEL SYNTHESIS (v54) ──────────────────────────────────────────
                try:
                    watch_kw = (cfg.get('watch_keywords') if isinstance(cfg, dict) else None)
                    synth = synthesize_intel(target or image_url, mode, items, watch_kw, emit=emit)
                    emit('result', {
                        'type':'intel_summary',
                        'source':'INTEL',
                        'platform':'🧠 Intelligence Synthesis',
                        'title': f"Verdict: {synth['confidence']['verdict']} ({synth['confidence']['score']})",
                        'snippet': f"clusters={len(synth['clusters'])} · graph nodes={synth['graph']['metrics'].get('nodes',0)} · alerts={len(synth['watchlist'])}",
                    })
                except Exception as _ie:
                    emit('log', f'⚠️ intel synthesis failed: {_ie}')

                if do_rep and items:
                    try:
                        h, j = save_report(target or image_url, mode, items)
                        report_path = str(h)
                        emit("log", f"📊 Report saved: {h.name}")
                        # ── Stream final reports to Telegram (HTML + JSON) ──
                        if do_tg and TELEGRAM_ENABLED:
                            try:
                                cap = f"📁 ALKASER OSINT v51 — Final Report\n🎯 {target or image_url}\n📊 Mode: {mode.upper()} | {len(items)} items"
                                tg_doc(h.read_bytes(), h.name, cap + "\n📄 HTML Report")
                                tg_doc(j.read_bytes(), j.name, cap + "\n🧬 JSON Data")
                                emit("log", "📨 Reports dispatched to Telegram (HTML + JSON)")
                            except Exception as e:
                                emit("log", f"⚠️ TG report send failed: {e}")
                    except Exception as e:
                        emit("log", f"⚠️ Report save failed: {e}")

                emit("done", {"total": len(items), "report": report_path})

        t = threading.Thread(target=runner, daemon=True)
        t.start()

        # Keep WS alive while runner works
        while t.is_alive():
            try:
                raw = await asyncio.wait_for(ws.receive_text(), timeout=1.0)
                if raw:
                    p = json.loads(raw)
                    if p.get("action") == "stop":
                        stop_flag.set()
                        break
            except asyncio.TimeoutError:
                pass
            except WebSocketDisconnect:
                stop_flag.set()
                break
            except Exception:
                break

        stop_flag.set()

    except WebSocketDisconnect:
        stop_flag.set()
    except Exception as e:
        logging.exception("ws_endpoint")
        stop_flag.set()


# ═══════════════════════════════════════════════════════════════════════
#  ENTRY POINT
# ═══════════════════════════════════════════════════════════════════════
if __name__ == "__main__":
    print(f"""
╔══════════════════════════════════════════════════════════════════════════╗
║         ALKASER OSINT v51.0 — MILITARY INTEL EDITION                     ║
║         نظام الاستخبارات مفتوح المصدر الجيل الخامس                                       ║
╠══════════════════════════════════════════════════════════════════════════╣
║  🌐 Local:    http://localhost:{PORT:<5}                                   ║
║  📂 Reports:  {str(REPORTS_DIR)[:54]:<54}  ║
║  📋 Logs:     {str(LOG_FILE)[:54]:<54}  ║
╠══════════════════════════════════════════════════════════════════════════╣
║  ✅ {len(SOCIAL_PLATFORMS):3} social platforms                                              ║
║  ✅ 15 search engines                                                    ║
║  ✅ 5 reverse image engines                                              ║
║  ✅ Phone owner identification                                           ║
║  ✅ Exact 3-4 word name matching                                         ║
║  ✅ Full Wayback Machine + Archive.org                                   ║
║  ✅ AI Arabic intelligence reports                                       ║
║  ✅ Telegram streaming enabled                                           ║
╠══════════════════════════════════════════════════════════════════════════╣
║  DDG Available: {str(DUCKDUCKGO_AVAILABLE):<10}  TG Bot: {"Enabled" if TELEGRAM_ENABLED else "Disabled":<10}           ║
╚══════════════════════════════════════════════════════════════════════════╝
""")

    # ═══════════════════════════════════════════════════════════════════
    # إعداد ngrok تلقائياً
    # ═══════════════════════════════════════════════════════════════════
    import subprocess, sys
    try:
        from pyngrok import ngrok
    except ImportError:
        subprocess.check_call([sys.executable, "-m", "pip", "install", "-q", "pyngrok"])
        from pyngrok import ngrok

    # ⚠️ استبدل هذا برمز المصادقة الخاص بك من https://dashboard.ngrok.com/get-started/your-authtoken
    NGROK_TOKEN = "3D7jdtzHrAWcvB3wrvii9O9XQWK_5xxaxx8csciSnyQWnNz2b"
    ngrok.set_auth_token(NGROK_TOKEN)

    try:
        ngrok.kill()
    except:
        pass

    tunnel = ngrok.connect(PORT, "http")
    public_url = tunnel.public_url
    print(f"📱 PUBLIC URL: {public_url}\n")

    # ═══════════════════════════════════════════════════════════════════
    # تشغيل الخادم
    # ═══════════════════════════════════════════════════════════════════
    import nest_asyncio
    nest_asyncio.apply()

    config = uvicorn.Config(
        app,
        host=HOST,
        port=PORT,
        log_level="warning",
        access_log=False,
        timeout_keep_alive=120,
    )
    server = uvicorn.Server(config)
    loop = asyncio.get_event_loop()
    print(f">>> افتح هذا الرابط: {public_url}\n")
    loop.run_until_complete(server.serve())


# ═══════════════════════════════════════════════════════════════════════════
#  ███ EXTENDED INTELLIGENCE REFERENCE LIBRARY v54 ███
#  Static knowledge banks used by the engine: dork catalogues, breach source
#  registry, country/language hints, TLD reputation map, OSINT cheatsheet.
#  These tables are consulted by the dork engine and watchlist scanner.
# ═══════════════════════════════════════════════════════════════════════════

# ─── Expanded Country / Locale Hints ──────────────────────────────────────
_COUNTRY_HINTS_EXT = {
    "🇵🇸 Palestine":  ["palestine", "gaza", "ramallah", "فلسطين", "غزة", "رام الله", "الخليل"],
    "🇸🇩 Sudan":      ["sudan", "khartoum", "السودان", "الخرطوم"],
    "🇸🇴 Somalia":    ["somalia", "mogadishu", "الصومال", "مقديشو"],
    "🇲🇷 Mauritania": ["mauritania", "nouakchott", "موريتانيا", "نواكشط"],
    "🇧🇭 Bahrain":    ["bahrain", "manama", "البحرين", "المنامة"],
    "🇴🇲 Oman":       ["oman", "muscat", "عمان", "مسقط"],
    "🇨🇦 Canada":     ["canada", "toronto", "montreal", "vancouver", "ottawa"],
    "🇦🇺 Australia":  ["australia", "sydney", "melbourne", "brisbane"],
    "🇮🇳 India":      ["india", "mumbai", "delhi", "bangalore", "kolkata"],
    "🇵🇰 Pakistan":   ["pakistan", "karachi", "lahore", "islamabad"],
    "🇧🇩 Bangladesh": ["bangladesh", "dhaka"],
    "🇮🇩 Indonesia":  ["indonesia", "jakarta", "surabaya"],
    "🇲🇾 Malaysia":   ["malaysia", "kuala lumpur"],
    "🇸🇬 Singapore":  ["singapore"],
    "🇯🇵 Japan":      ["japan", "tokyo", "osaka", "kyoto"],
    "🇰🇷 Korea":      ["korea", "seoul", "busan"],
    "🇨🇳 China":      ["china", "beijing", "shanghai", "shenzhen", "guangzhou"],
    "🇧🇷 Brazil":     ["brazil", "brasil", "sao paulo", "rio de janeiro"],
    "🇲🇽 Mexico":     ["mexico", "ciudad de mexico", "guadalajara"],
    "🇦🇷 Argentina":  ["argentina", "buenos aires"],
    "🇪🇸 Spain":      ["spain", "madrid", "barcelona", "españa"],
    "🇮🇹 Italy":      ["italy", "rome", "milan", "italia"],
    "🇳🇱 Netherlands":["netherlands", "amsterdam"],
    "🇧🇪 Belgium":    ["belgium", "brussels"],
    "🇨🇭 Switzerland":["switzerland", "zurich", "geneva", "bern"],
    "🇸🇪 Sweden":     ["sweden", "stockholm"],
    "🇳🇴 Norway":     ["norway", "oslo"],
    "🇩🇰 Denmark":    ["denmark", "copenhagen"],
    "🇫🇮 Finland":    ["finland", "helsinki"],
    "🇵🇱 Poland":     ["poland", "warsaw", "krakow"],
    "🇨🇿 Czechia":    ["czech", "prague"],
    "🇺🇦 Ukraine":    ["ukraine", "kyiv", "kiev"],
    "🇷🇴 Romania":    ["romania", "bucharest"],
    "🇬🇷 Greece":     ["greece", "athens"],
    "🇵🇹 Portugal":   ["portugal", "lisbon"],
    "🇮🇪 Ireland":    ["ireland", "dublin"],
    "🇿🇦 South Africa":["south africa", "johannesburg", "cape town"],
    "🇳🇬 Nigeria":    ["nigeria", "lagos", "abuja"],
    "🇰🇪 Kenya":      ["kenya", "nairobi"],
    "🇪🇹 Ethiopia":   ["ethiopia", "addis ababa"],
    "🇮🇱 Israel":     ["israel", "tel aviv"],
    "🇦🇿 Azerbaijan": ["azerbaijan", "baku"],
    "🇰🇿 Kazakhstan": ["kazakhstan", "almaty", "astana", "nur-sultan"],
    "🇺🇿 Uzbekistan": ["uzbekistan", "tashkent"],
    "🇦🇫 Afghanistan":["afghanistan", "kabul"],
    "🇹🇭 Thailand":   ["thailand", "bangkok"],
    "🇻🇳 Vietnam":    ["vietnam", "hanoi", "ho chi minh"],
    "🇵🇭 Philippines":["philippines", "manila"],
    "🇳🇿 New Zealand":["new zealand", "auckland", "wellington"],
}
_COUNTRY_HINTS.update(_COUNTRY_HINTS_EXT)


# ─── TLD Reputation Map ───────────────────────────────────────────────────
TLD_REPUTATION = {
    # Generally trusted corporate / educational
    ".gov":  "official-government",
    ".edu":  "academic",
    ".mil":  "military",
    ".int":  "international-org",
    ".org":  "non-profit",
    ".com":  "commercial",
    ".net":  "network",
    # Frequently abused for spam / phishing per public reports
    ".xyz":  "high-abuse",
    ".top":  "high-abuse",
    ".tk":   "high-abuse",
    ".ml":   "high-abuse",
    ".ga":   "high-abuse",
    ".cf":   "high-abuse",
    ".gq":   "high-abuse",
    ".info": "medium-abuse",
    ".click":"medium-abuse",
    ".loan": "medium-abuse",
    ".work": "medium-abuse",
    ".click":"medium-abuse",
    ".pw":   "medium-abuse",
    # Country code samples
    ".sa": "country-sa", ".ae": "country-ae", ".eg": "country-eg",
    ".iq": "country-iq", ".jo": "country-jo", ".kw": "country-kw",
    ".uk": "country-uk", ".us": "country-us", ".de": "country-de",
    ".fr": "country-fr", ".ru": "country-ru", ".cn": "country-cn",
    ".jp": "country-jp", ".kr": "country-kr", ".in": "country-in",
    ".br": "country-br", ".mx": "country-mx", ".ca": "country-ca",
}


def tld_reputation(domain: str) -> str:
    d = (domain or "").lower().strip().rstrip("/")
    for tld, label in sorted(TLD_REPUTATION.items(), key=lambda x: -len(x[0])):
        if d.endswith(tld):
            return label
    return "unknown"


# ─── Breach Source Registry (public catalogue references only) ────────────
BREACH_SOURCES = [
    {"name": "HaveIBeenPwned",        "kind": "service",  "url": "https://haveibeenpwned.com/", "scope": "email/phone"},
    {"name": "DeHashed",              "kind": "service",  "url": "https://dehashed.com/",       "scope": "email/username/phone"},
    {"name": "LeakCheck",             "kind": "service",  "url": "https://leakcheck.io/",        "scope": "email/username"},
    {"name": "IntelligenceX",         "kind": "service",  "url": "https://intelx.io/",           "scope": "email/domain/wallet"},
    {"name": "Snusbase",              "kind": "service",  "url": "https://snusbase.com/",        "scope": "email/username/ip"},
    {"name": "Scylla",                "kind": "archive",  "url": "https://scylla.so/",           "scope": "email/username"},
    {"name": "Wayback Machine",       "kind": "archive",  "url": "https://web.archive.org/",     "scope": "url-history"},
    {"name": "Archive.today",         "kind": "archive",  "url": "https://archive.ph/",          "scope": "url-snapshots"},
    {"name": "Common Crawl",          "kind": "corpus",   "url": "https://commoncrawl.org/",     "scope": "web-corpus"},
    {"name": "GitHub Code Search",    "kind": "code",     "url": "https://github.com/search",    "scope": "leaked-code/secrets"},
    {"name": "GitLab Code Search",    "kind": "code",     "url": "https://gitlab.com/search",    "scope": "leaked-code/secrets"},
    {"name": "Pastebin Search",       "kind": "paste",    "url": "https://pastebin.com/",        "scope": "text-paste"},
    {"name": "Ghostbin",              "kind": "paste",    "url": "https://ghostbin.site/",       "scope": "text-paste"},
    {"name": "Rentry.co",             "kind": "paste",    "url": "https://rentry.co/",           "scope": "text-paste"},
    {"name": "Justpaste.it",          "kind": "paste",    "url": "https://justpaste.it/",        "scope": "text-paste"},
    {"name": "BreachForums Archive",  "kind": "forum",    "url": "https://breachforums.st/",     "scope": "leak-forum"},
]


# ─── OSINT Dork Catalogue (expanded) ──────────────────────────────────────
DORK_CATALOGUE = {
    "social": [
        'site:twitter.com "{q}"',
        'site:x.com "{q}"',
        'site:facebook.com "{q}"',
        'site:instagram.com "{q}"',
        'site:tiktok.com "{q}"',
        'site:linkedin.com "{q}"',
        'site:reddit.com "{q}"',
        'site:youtube.com "{q}"',
        'site:medium.com "{q}"',
        'site:t.me "{q}"',
        'site:vk.com "{q}"',
        'site:weibo.com "{q}"',
        'site:threads.net "{q}"',
        'site:bsky.app "{q}"',
        'site:mastodon.social "{q}"',
    ],
    "code": [
        'site:github.com "{q}"',
        'site:gitlab.com "{q}"',
        'site:bitbucket.org "{q}"',
        'site:gist.github.com "{q}"',
        'site:codepen.io "{q}"',
        'site:replit.com "{q}"',
        'site:huggingface.co "{q}"',
    ],
    "paste": [
        'site:pastebin.com "{q}"',
        'site:ghostbin.site "{q}"',
        'site:hastebin.com "{q}"',
        'site:rentry.co "{q}"',
        'site:justpaste.it "{q}"',
        'site:controlc.com "{q}"',
        'site:0bin.net "{q}"',
    ],
    "docs": [
        '"{q}" filetype:pdf',
        '"{q}" filetype:doc OR filetype:docx',
        '"{q}" filetype:xls OR filetype:xlsx',
        '"{q}" filetype:ppt OR filetype:pptx',
        '"{q}" filetype:txt',
        '"{q}" filetype:csv',
        '"{q}" filetype:json',
        '"{q}" filetype:xml',
        '"{q}" filetype:sql',
        '"{q}" filetype:log',
    ],
    "leaks": [
        '"{q}" intext:"password"',
        '"{q}" intext:"passwd"',
        '"{q}" intext:"hash"',
        '"{q}" intext:"breach"',
        '"{q}" intext:"leak"',
        '"{q}" intext:"dump"',
        '"{q}" intext:"credentials"',
        '"{q}" intext:"api_key"',
        '"{q}" intext:"token"',
        '"{q}" intext:"secret"',
        '"{q}" "تسريب"',
        '"{q}" "اختراق"',
    ],
    "configs": [
        '"{q}" intitle:"index of"',
        '"{q}" inurl:wp-config.php',
        '"{q}" inurl:.env',
        '"{q}" inurl:config.php',
        '"{q}" filetype:env',
        '"{q}" filetype:ini',
        '"{q}" filetype:yml OR filetype:yaml',
        '"{q}" filetype:cfg',
    ],
    "archive": [
        'site:web.archive.org "{q}"',
        'site:archive.ph "{q}"',
        'site:archive.org "{q}"',
        'site:cachedview.nl "{q}"',
    ],
    "people": [
        '"{q}" "phone"',
        '"{q}" "email"',
        '"{q}" "address"',
        '"{q}" "cv" OR "resume"',
        '"{q}" "bio"',
        '"{q}" "profile"',
    ],
    "forum": [
        'site:quora.com "{q}"',
        'site:stackexchange.com "{q}"',
        'site:stackoverflow.com "{q}"',
        'site:disqus.com "{q}"',
        'site:4chan.org "{q}"',
        'site:hackernews.com "{q}"',
    ],
}


def build_dork_pack(query: str, categories=None) -> list:
    cats = categories or list(DORK_CATALOGUE.keys())
    out = []
    for c in cats:
        for tpl in DORK_CATALOGUE.get(c, []):
            out.append({"category": c, "query": tpl.replace("{q}", query)})
    return out


# ─── OSINT Cheatsheet (delivered with the report metadata) ────────────────
OSINT_CHEATSHEET = {
    "username": [
        "Run reverse handle searches across the canonical 100+ platform set.",
        "Pivot on collected emails / domains found in bios.",
        "Snapshot every profile to detect renaming / wipes later.",
    ],
    "email": [
        "Check breach catalogues (HIBP, DeHashed, IntelligenceX).",
        "Inspect MX, SPF, DMARC of the email's domain.",
        "Search the local-part as a username across major platforms.",
    ],
    "phone": [
        "Normalize to E.164 and run number-validation lookups.",
        "Check messenger registration (Telegram, WhatsApp signals).",
        "Cross-reference public business listings and classifieds.",
    ],
    "domain": [
        "WHOIS / RDAP, reverse-IP, subdomain enumeration.",
        "Wayback timeline + Common Crawl historical extracts.",
        "Search code-search engines for hardcoded references.",
    ],
    "name": [
        "Use strict full-name matching with diacritic-insensitive compare.",
        "Geo-bias the queries with country / city hints.",
        "Mine professional networks (LinkedIn dorks, academic indexes).",
    ],
    "deep": [
        "Combine archive + paste + leak categories of the dork pack.",
        "Stream every hit into the snapshot store for diffing.",
        "Embed AI synthesis at the end of the report only — never mid-run.",
    ],
    "darkweb": [
        "Route ONLY dark-web engines through SOCKS5 / Tor.",
        "Never re-use clearnet headers when probing .onion endpoints.",
        "Down-rank unreachable mirrors; keep canonical-mirror map fresh.",
    ],
    "image": [
        "Fan out to Yandex + Bing + Google Lens + TinEye + SauceNAO.",
        "Extract EXIF (GPS / device / timestamp) when present.",
        "Run face crop variations to defeat naive crop-watermark filters.",
    ],
}


# ─── Module self-test (callable on demand) ────────────────────────────────
def intel_selftest() -> dict:
    """Run a tiny synthetic pass through the v54 intelligence pipeline."""
    sample = [
        {"platform": "Twitter",   "title": "user @alpha posted",
         "snippet": "contact alpha@example.com tel +14155551212  https://alpha.dev",
         "url": "https://twitter.com/alpha"},
        {"platform": "GitHub",    "title": "alpha pushed code",
         "snippet": "alpha@example.com committed to repo, see also @alpha",
         "url": "https://github.com/alpha"},
        {"platform": "PasteSite", "title": "leak dump 2024",
         "snippet": "alpha@example.com:hunter2 breach password leak",
         "url": "https://pastebin.com/xyz"},
        {"platform": "Reddit",    "title": "user_alpha thread",
         "snippet": "user_alpha mentions Riyadh travel, posted 2024-03-15 14:22",
         "url": "https://reddit.com/u/alpha"},
    ]
    synth = synthesize_intel("alpha", "username", sample,
                             watch_keywords=["leak", "breach"])
    return {
        "verdict":      synth["confidence"]["verdict"],
        "clusters":     len(synth["clusters"]),
        "graph_nodes":  synth["graph"]["metrics"]["nodes"],
        "graph_edges":  synth["graph"]["metrics"]["edges"],
        "alerts":       len(synth["watchlist"]),
        "anomalies":    len(synth["anomalies"]),
        "geo_primary":  synth["geo"].get("primary"),
        "snapshot":     synth.get("snapshot"),
    }


@app.get("/api/selftest")
def api_selftest():
    try:
        return {"status": "ok", "result": intel_selftest()}
    except Exception as e:
        return {"status": "error", "error": str(e)}


@app.get("/api/dorks")
def api_dorks(q: str = "", cats: str = ""):
    cat_list = [c.strip() for c in cats.split(",") if c.strip()] or None
    return {"query": q, "dorks": build_dork_pack(q, cat_list) if q else []}


@app.get("/api/breach-sources")
def api_breach_sources():
    return {"sources": BREACH_SOURCES}


@app.get("/api/cheatsheet")
def api_cheatsheet(mode: str = ""):
    if mode and mode in OSINT_CHEATSHEET:
        return {"mode": mode, "tips": OSINT_CHEATSHEET[mode]}
    return {"all": OSINT_CHEATSHEET}


# ═══════════════════════════════════════════════════════════════════════════
#  END OF ALKASER OSINT v54 — INTELLIGENCE PLATFORM EXTENSION BLOCK
# ═══════════════════════════════════════════════════════════════════════════
