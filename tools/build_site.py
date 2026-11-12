#!/usr/bin/env python3
"""Generates the static site (site/*.html) from the templates below and the README changelog.
Run after editing: python3 tools/build_site.py   (Cloudflare Pages serves the site/ folder as is)."""
import html, os, re

ROOT = os.path.join(os.path.dirname(__file__), "..")
SITE = os.path.join(ROOT, "site")

NAV = [("index.html", "Início"), ("features.html", "Recursos"), ("download.html", "Download"),
       ("changelog.html", "Versões"), ("docs.html", "Documentação"), ("faq.html", "FAQ")]


def page(name, title, body, extra_head=""):
    nav = "".join(f'<a href="{h}"{" class=\"on\"" if h == name else ""}>{t}</a>' for h, t in NAV)
    return f"""<!doctype html>
<html lang="pt-BR"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>{html.escape(title)} · 1227</title><meta name="description" content="1227: client visual de Minecraft 1.8.9 para PvP.">
<link rel="icon" href="assets/favicon.png"><link rel="stylesheet" href="assets/site.css">{extra_head}</head>
<body><header class="top"><a class="brand" href="index.html"><img src="assets/logo.png" alt=""><span>1227</span></a><nav>{nav}</nav></header>
<main>{body}</main>
<footer><div>1227 é um projeto independente e não é afiliado à Mojang Studios nem à Microsoft. Minecraft é marca da Mojang Studios.</div>
<div><a href="privacy.html">Privacidade</a> · <a href="terms.html">Termos de uso</a> · <a href="docs.html#suporte">Suporte</a></div></footer>
</body></html>
"""


def md_inline(s):
    s = html.escape(s)
    s = re.sub(r"\*\*(.+?)\*\*", r"<b>\1</b>", s)
    s = re.sub(r"`(.+?)`", r"<code>\1</code>", s)
    return s


def md_list(lines):
    """Minimal nested '- ' list renderer (2-space indents)."""
    out, depth = [], -1
    for ln in lines:
        m = re.match(r"^(\s*)- (.*)$", ln)
        if not m:
            continue
        d = len(m.group(1)) // 2
        while depth < d:
            out.append("<ul>")
            depth += 1
        while depth > d:
            out.append("</ul>")
            depth -= 1
        out.append(f"<li>{md_inline(m.group(2))}</li>")
    out.extend("</ul>" for _ in range(depth + 1))
    return "".join(out)


def changelog():
    text = open(os.path.join(ROOT, "README.md"), encoding="utf-8").read()
    parts = re.split(r"^## Mudanças na v([\d.]+)\s*$", text, flags=re.M)
    sections = []
    for i in range(1, len(parts), 2):
        ver, body = parts[i], parts[i + 1]
        body = body.split("\n## ")[0]
        sections.append((ver, md_list(body.splitlines())))
    sections.sort(key=lambda x: [int(n) for n in x[0].split(".")], reverse=True)
    return "".join(f'<section class="card"><h2>v{v}</h2>{b}</section>' for v, b in sections)


FEATURES = [
    ("Menu e HUD", ["Menu com 11 estilos (Liquid Glass, Aurora, Gradient, Midnight, OLED, Neon e outros), busca, favoritos e configurações em janela",
                    "Editor de HUD com arrastar, snap e guias, trava por elemento e 3 layouts salvos (com copiar e colar)",
                    "15 elementos de HUD: Watermark, Info, Keystrokes, Target, Combo, Armor, Potions, Coordenadas, Movimento, Held Item, Relógio, Memória, Servidor, Bússola e Estatísticas da sessão",
                    "Dynamic Island com cartão de combate e notificações"]),
    ("Combate (só visual)", ["Hit Effect, Hit Marker, Damage Numbers e Kill Effects (inclui ragdoll e decapitação)",
                             "Shockwave com 9 estilos, incluindo Block Wave",
                             "Weapon Effects (40 efeitos), Item Glow e 18 modelos 3D de espadas e facas com finishes e inspect",
                             "Hit Sounds sintetizados, com pitch, variação, combo e limite de sobreposição"]),
    ("Mundo e jogador", ["50 céus dinâmicos com camada GLSL (nebulosa, aurora, nuvens volumétricas e tempestade)",
                         "Ambience com 12 atmosferas, partículas ambientes e World (hora e clima só na sua tela)",
                         "Auras, Halo, Aura Hat, Wings, Trails, Jump Effects e Player Glow",
                         "22 emotes e danças, numa roda de seleção"]),
    ("Câmera e jogo", ["Motion Blur, Zoom, Static FOV, View Bobbing e Camera FX",
                       "1.7 Animations e Toggle Sprint (com as regras do vanilla)",
                       "Presets de desempenho (Low-end, High FPS, Balanced e Visual quality) e limite de partículas"]),
]


def features():
    cards = "".join(f'<section class="card"><h2>{t}</h2><ul>{"".join(f"<li>{html.escape(i)}</li>" for i in items)}</ul></section>'
                    for t, items in FEATURES)
    return f"""<section class="hero small"><h1>Recursos</h1><p class="muted">Tudo abaixo existe e funciona no mod atual. O 1227 é só visual e de qualidade de vida: não muda alcance, tempo de ataque, pacotes nem nada que dê vantagem.</p></section>
<div class="grid">{cards}</div>"""


INDEX = """<section class="hero"><div class="eyebrow">Minecraft 1.8.9 · Forge</div><h1>Seu PvP,<br>do seu jeito.</h1>
<p class="muted">Visual, HUD e efeitos feitos para 1.8.9. Leve, configurável e sem vantagens ilegítimas.</p>
<div class="cta"><a class="btn primary" href="download.html">Baixar</a><a class="btn" href="features.html">Ver recursos</a></div></section>
<div class="grid three">
<section class="card"><h2>Interface própria</h2><p class="muted">Menu com Liquid Glass, busca, favoritos e editor de HUD com snap e layouts.</p></section>
<section class="card"><h2>Efeitos em tempo real</h2><p class="muted">Céus em GLSL, auras, shockwaves, armas 3D e kill effects, todos renderizados ao vivo.</p></section>
<section class="card"><h2>Seguro por design</h2><p class="muted">Sem senhas, sem pacotes extras, sem cheats. Downloads verificados por SHA-256.</p></section>
</div>"""

DOWNLOAD = """<section class="hero small"><h1>Download</h1><p class="muted">Requer Minecraft Java Edition original, Forge 1.8.9 (11.15.1.2318) e Java 8.</p></section>
<section class="card" id="latest"><h2>Última versão</h2><p class="muted" id="latest-text">Consultando…</p></section>
<section class="card"><h2>Instalação manual</h2><ol>
<li>Instale o Forge 1.8.9 - 11.15.1.2318 pelo instalador oficial do Forge.</li>
<li>Coloque o arquivo <code>1227-x.y.z.jar</code> em <code>.minecraft/mods</code>.</li>
<li>Abra o jogo com o perfil Forge 1.8.9 e aperte <b>Right Ctrl</b> para abrir o menu.</li></ol>
<p class="muted">Confira o arquivo com o SHA-256 mostrado acima (no Windows: <code>certutil -hashfile arquivo.jar SHA256</code>).</p></section>
<section class="card"><h2>Launcher 1227</h2><p class="muted">O launcher (Windows) instala, verifica, atualiza e restaura versões do mod, numa instância própria ou no seu Forge existente. Ele é publicado junto com as releases quando estiver pronto para distribuição.</p></section>
<script src="assets/download.js"></script>"""

DOCS = """<section class="hero small"><h1>Documentação</h1></section>
<section class="card"><h2>Instalação</h2><p>Veja a página <a href="download.html">Download</a>. O 1227 precisa do Forge 1.8.9 (11.15.1.2318) e Java 8.</p></section>
<section class="card"><h2>Primeiros passos</h2><ul>
<li><b>Abrir o menu:</b> Right Ctrl (dá para trocar no módulo Client ou em Opções › Controles › 1227).</li>
<li><b>Ligar e desligar:</b> clique no card do módulo. Engrenagem ou botão direito abre as configurações.</li>
<li><b>Editar HUD:</b> Client › Edit HUD layout ou <code>/1227 hud</code>. Arraste, role para mudar o tamanho, botão direito para resetar, botão do meio para travar. Os botões Save/Load guardam até 3 layouts.</li>
<li><b>Desempenho:</b> Settings › Performance tem presets de um clique e o limite de partículas.</li></ul></section>
<section class="card"><h2>Configurações e backups</h2><ul>
<li>Tudo fica em <code>config/1227.json</code>, salvo automaticamente.</li>
<li>Cada vez que o jogo abre, uma cópia vai para <code>config/1227-backups/</code> (as 5 mais recentes). Para restaurar, feche o jogo e copie um backup por cima de <code>1227.json</code>.</li>
<li>Perfis: Settings › Presets (Save/Load profile). Exportar e importar: Settings › Client (Copy config to clipboard / Load config from clipboard).</li>
<li>Layouts de HUD: <code>config/1227-hud-layouts.json</code>.</li></ul></section>
<section class="card"><h2>Sons próprios</h2><p>Coloque arquivos WAV na pasta aberta por Hit Sounds › Open sounds folder e use Reload custom sounds.</p></section>
<section class="card" id="suporte"><h2>Suporte</h2><p>Abra uma issue no repositório do projeto no GitHub com: versão do 1227, launcher usado, e o arquivo <code>logs/latest.log</code>. Nunca envie senhas ou tokens.</p></section>"""

FAQ = """<section class="hero small"><h1>Perguntas frequentes</h1></section>
<section class="card"><h2>O 1227 é permitido nos servidores?</h2><p class="muted">Ele é só visual e não envia pacotes, mas cada servidor tem as próprias regras. Confira as regras do servidor antes de jogar.</p></section>
<section class="card"><h2>Funciona com o Lunar, Badlion ou Feather?</h2><p class="muted">O 1227 é um mod Forge. Funciona onde há suporte a mods Forge 1.8.9, por exemplo no perfil "1.8.9 with Forge" do Lunar. Cada client tem limites próprios, e alguns recursos podem conflitar com recursos equivalentes desses clients.</p></section>
<section class="card"><h2>Preciso de Minecraft original?</h2><p class="muted">Sim. O 1227 não distribui o jogo e não contorna a autenticação. Servidores online exigem conta oficial.</p></section>
<section class="card"><h2>Por que a vida do adversário aparece fixa?</h2><p class="muted">Alguns servidores não enviam a vida real dos outros jogadores. Quando o servidor mostra a vida no placar, o 1227 usa esse valor.</p></section>
<section class="card"><h2>O 1227 aumenta o FPS?</h2><p class="muted">Não prometemos ganho de FPS. Os presets de desempenho reduzem o custo dos efeitos do próprio 1227.</p></section>"""

PRIVACY = """<section class="hero small"><h1>Política de privacidade</h1></section>
<section class="card"><ul>
<li>O mod 1227 não coleta, não envia e não guarda dados pessoais. As configurações ficam só no seu computador.</li>
<li>O launcher consulta a API do 1227 para saber as versões e as notícias. Essa consulta não envia conta, nome de usuário nem identificador; o provedor de hospedagem (Cloudflare) pode registrar o IP e o horário da requisição nos logs técnicos dele.</li>
<li>Os downloads vêm do GitHub Releases, sujeitos à política de privacidade do GitHub.</li>
<li>O launcher nunca pede nem guarda a senha da sua conta Microsoft.</li></ul></section>"""

TERMS = """<section class="hero small"><h1>Termos de uso</h1></section>
<section class="card"><ul>
<li>O 1227 é fornecido "como está", sem garantias. Use por sua conta e risco.</li>
<li>É proibido usar o 1227 para violar regras de servidores, os Termos de Serviço do Minecraft ou as Diretrizes de Uso da Mojang.</li>
<li>O 1227 não é afiliado à Mojang Studios nem à Microsoft e não distribui arquivos do Minecraft.</li>
<li>Fontes: Poppins (SIL Open Font License). Modelos da butterfly knife e do Tung Tung Sahur: fornecidos pelo autor do projeto.</li></ul></section>"""

PAGES = {
    "index.html": ("Início", INDEX),
    "features.html": ("Recursos", features()),
    "download.html": ("Download", DOWNLOAD),
    "changelog.html": ("Versões", '<section class="hero small"><h1>Versões</h1></section>' + changelog()),
    "docs.html": ("Documentação", DOCS),
    "faq.html": ("FAQ", FAQ),
    "privacy.html": ("Privacidade", PRIVACY),
    "terms.html": ("Termos", TERMS),
}

for name, (title, body) in PAGES.items():
    with open(os.path.join(SITE, name), "w", encoding="utf-8") as f:
        f.write(page(name, title, body))
print("site:", ", ".join(PAGES))
