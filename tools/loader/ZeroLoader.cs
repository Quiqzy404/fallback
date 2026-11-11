// ZERO Loader - installs the ZERO client jar into a Minecraft profile's mods folder.
// Fully custom-drawn window (ember particles, glass cards, animated Load button). No process injection.
// Build: csc /target:winexe /out:"ZERO Loader.exe" /resource:zero-client-1.0.0.jar,zero.jar /r:System.Management.dll /r:System.Windows.Forms.dll /r:System.Drawing.dll ZeroLoader.cs
using System;
using System.Collections.Generic;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Drawing.Text;
using System.IO;
using System.Linq;
using System.Management;
using System.Reflection;
using System.Windows.Forms;

static class Program {
    [STAThread]
    static void Main() {
        Application.EnableVisualStyles();
        Application.SetCompatibleTextRenderingDefault(false);
        Application.Run(new LoaderForm());
    }
}

class Prof {
    public string Name, Launcher, Mods, GameDir;
    public bool HasZero, HasApi;
}

class Spark { public float X, Y, Vy, R, Life, Sway; }
class Hit { public RectangleF R; public Action Do; public int Tag; }

class LoaderForm : Form {
    static readonly Color Void = Color.FromArgb(8, 6, 5);
    static readonly Color Card = Color.FromArgb(18, 14, 12);
    static readonly Color CardHi = Color.FromArgb(32, 24, 19);
    static readonly Color Line = Color.FromArgb(48, 36, 28);
    static readonly Color Text1 = Color.FromArgb(244, 238, 232);
    static readonly Color Muted = Color.FromArgb(150, 136, 126);
    static readonly Color Ember = Color.FromArgb(240, 120, 44);
    static readonly Color EmberHi = Color.FromArgb(255, 178, 110);
    static readonly Color Good = Color.FromArgb(126, 231, 135);
    static readonly Color Bad = Color.FromArgb(255, 150, 120);

    const int W = 620, H = 574, PAD = 30;
    readonly Timer timer = new Timer();
    readonly List<Spark> sparks = new List<Spark>();
    readonly List<Hit> hits = new List<Hit>();
    readonly Random rng = new Random();
    List<Prof> profiles = new List<Prof>();
    int selected = 0, hover = -1, scroll = 0;
    PointF mouse = new PointF(-1, -1);
    float t = 0, loadGlow = 0, pressT = 0;
    string status = "";
    Color statusCol = Muted;
    Point drag;
    bool dragging = false;
    Image preview;
    readonly string stateDir = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "ZERO");

    const int ListX = PAD, ListY = 262, ListW = W - PAD * 2, RowH = 46, Rows = 4;
    const int HeroH = 212;

    public LoaderForm() {
        Text = "ZERO Loader";
        FormBorderStyle = FormBorderStyle.None;
        StartPosition = FormStartPosition.CenterScreen;
        ClientSize = new Size(W, H);
        BackColor = Void;
        DoubleBuffered = true;
        try { Icon = Icon.ExtractAssociatedIcon(Application.ExecutablePath); } catch { }
        Region = RoundRegion(new Rectangle(0, 0, W, H), 16);
        try { using (var st = Assembly.GetExecutingAssembly().GetManifestResourceStream("preview.png")) if (st != null) preview = Image.FromStream(st); } catch { }

        for (int i = 0; i < 46; i++) sparks.Add(NewSpark(true));
        timer.Interval = 16;
        timer.Tick += (s, e) => { t += 0.016f; Step(); Invalidate(); };
        timer.Start();

        MouseDown += OnDown;
        MouseMove += OnMove;
        MouseUp += OnUp;
        MouseWheel += (s, e) => { scroll = Clamp(scroll - Math.Sign(e.Delta), 0, Math.Max(0, profiles.Count - Rows)); };
        KeyDown += (s, e) => { if (e.KeyCode == Keys.Escape) Close(); };

        Reload();
    }

    static Region RoundRegion(Rectangle r, int rad) {
        var p = new GraphicsPath();
        p.AddArc(r.X, r.Y, rad, rad, 180, 90);
        p.AddArc(r.Right - rad, r.Y, rad, rad, 270, 90);
        p.AddArc(r.Right - rad, r.Bottom - rad, rad, rad, 0, 90);
        p.AddArc(r.X, r.Bottom - rad, rad, rad, 90, 90);
        p.CloseFigure();
        return new Region(p);
    }

    static GraphicsPath Round(RectangleF r, float rad) {
        var p = new GraphicsPath();
        p.AddArc(r.X, r.Y, rad, rad, 180, 90);
        p.AddArc(r.Right - rad, r.Y, rad, rad, 270, 90);
        p.AddArc(r.Right - rad, r.Bottom - rad, rad, rad, 0, 90);
        p.AddArc(r.X, r.Bottom - rad, rad, rad, 90, 90);
        p.CloseFigure();
        return p;
    }

    Spark NewSpark(bool anywhere) {
        return new Spark {
            X = (float)rng.NextDouble() * W,
            Y = anywhere ? (float)rng.NextDouble() * H : H + 8,
            Vy = 0.2f + (float)rng.NextDouble() * 0.7f,
            R = 0.7f + (float)rng.NextDouble() * 1.9f,
            Life = (float)rng.NextDouble(),
            Sway = (float)rng.NextDouble() * 6.28f
        };
    }

    void Step() {
        foreach (var s in sparks) {
            s.Y -= s.Vy; s.Sway += 0.012f; s.X += (float)Math.Sin(s.Sway) * 0.3f; s.Life += 0.004f;
            if (s.Y < -8) { var n = NewSpark(false); s.X = n.X; s.Y = n.Y; s.Vy = n.Vy; s.R = n.R; s.Life = n.Life; s.Sway = n.Sway; }
        }
        float target = (selected >= 0 && profiles.Count > 0) ? 1 : 0;
        loadGlow += (target - loadGlow) * 0.1f;
        if (pressT > 0) pressT -= 0.06f;
    }

    // ---------------- profile discovery

    static IEnumerable<string> Dirs(string p) { try { return Directory.Exists(p) ? Directory.GetDirectories(p) : new string[0]; } catch { return new string[0]; } }

    static Prof Make(string name, string launcher, string gameDir, string mods) {
        var p = new Prof { Name = name, Launcher = launcher, GameDir = gameDir, Mods = mods };
        Inspect(p);
        return p;
    }

    static void Inspect(Prof p) {
        p.HasZero = false;
        p.HasApi = p.Launcher == "NoRisk";
        if (!Directory.Exists(p.Mods)) return;
        foreach (var f in Directory.GetFiles(p.Mods, "*.jar")) {
            var n = Path.GetFileName(f).ToLowerInvariant();
            if (n.StartsWith("zero-client")) p.HasZero = true;
            if (n.StartsWith("fabric-api")) p.HasApi = true;
        }
    }

    List<Prof> Find() {
        var r = new List<Prof>();
        string ad = Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData);
        string mc = Path.Combine(ad, ".minecraft");
        if (Directory.Exists(mc)) r.Add(Make("Default (.minecraft)", "Minecraft", mc, Path.Combine(mc, "mods")));
        foreach (var root in new[] { @"D:\minecraft norisk\profiles", Path.Combine(ad, "norisk", "profiles"), Path.Combine(ad, "NoRiskClient", "profiles") })
            foreach (var d in Dirs(root)) r.Add(Make(Path.GetFileName(d), "NoRisk", d, Path.Combine(d, "mods")));
        foreach (var root in new[] { Path.Combine(ad, "ModrinthApp", "profiles"), Path.Combine(ad, "com.modrinth.theseus", "profiles") })
            foreach (var d in Dirs(root)) r.Add(Make(Path.GetFileName(d), "Modrinth", d, Path.Combine(d, "mods")));
        foreach (var d in Dirs(Path.Combine(ad, "PrismLauncher", "instances"))) {
            string gd = Directory.Exists(Path.Combine(d, ".minecraft")) ? Path.Combine(d, ".minecraft") : Path.Combine(d, "minecraft");
            if (Directory.Exists(gd)) r.Add(Make(Path.GetFileName(d), "Prism", gd, Path.Combine(gd, "mods")));
        }
        foreach (var d in Dirs(Path.Combine(ad, ".dawn", "profiles"))) {
            string gd = Path.Combine(d, ".minecraft");
            if (Directory.Exists(gd)) r.Add(Make(Path.GetFileName(d), "Feather", gd, Path.Combine(gd, "mods")));
        }
        try {
            string cf = Path.Combine(stateDir, "custom.txt");
            if (File.Exists(cf))
                foreach (var l in File.ReadAllLines(cf).Where(x => x.Trim().Length > 0 && Directory.Exists(x.Trim())))
                    r.Add(Make(Path.GetFileName(l.Trim()), "Custom", l.Trim(), Path.Combine(l.Trim(), "mods")));
        } catch { }
        return r.OrderByDescending(p => p.HasZero).ThenByDescending(p => p.HasApi).ToList();
    }

    void Reload() {
        string keep = (selected >= 0 && selected < profiles.Count) ? profiles[selected].GameDir : "";
        profiles = Find();
        selected = Math.Max(0, profiles.FindIndex(p => p.GameDir == keep));
        if (profiles.Count == 0) selected = -1;
        UpdateStatus();
    }

    void UpdateStatus() {
        if (selected < 0 || selected >= profiles.Count) { status = "No Minecraft profile found. Use Browse to pick a folder."; statusCol = Muted; return; }
        var p = profiles[selected];
        if (!p.HasApi) { status = "No Fabric API here - fine if your launcher adds it, otherwise install Fabric API 1.21.11."; statusCol = Bad; }
        else if (p.HasZero) { status = "ZERO is installed in this profile. Press Reload to update it."; statusCol = Good; }
        else { status = "Ready. Press Load, then start this profile and tap Right Shift in game."; statusCol = Muted; }
    }

    // ---------------- actions

    static bool Running(Prof p) {
        try {
            using (var q = new ManagementObjectSearcher("SELECT CommandLine FROM Win32_Process WHERE Name='javaw.exe' OR Name='java.exe'"))
                foreach (ManagementObject o in q.Get())
                    if ((o["CommandLine"] ?? "").ToString().IndexOf(p.GameDir, StringComparison.OrdinalIgnoreCase) >= 0) return true;
        } catch { }
        return false;
    }

    void DoLoad() {
        if (selected < 0) return;
        var p = profiles[selected];
        pressT = 1;
        try {
            Directory.CreateDirectory(p.Mods);
            foreach (var f in Directory.GetFiles(p.Mods, "zero-client*.jar")) File.Delete(f);
            using (var s = Assembly.GetExecutingAssembly().GetManifestResourceStream("zero.jar"))
            using (var o = File.Create(Path.Combine(p.Mods, "zero-client-1.0.0.jar")))
                s.CopyTo(o);
            Inspect(p);
            status = Running(p) ? "Loaded. Minecraft is open with this profile - restart it to use ZERO." : "Loaded into " + p.Name + ". Start it and press Right Shift.";
            statusCol = Running(p) ? EmberHi : Good;
        } catch (IOException) { status = "mods folder is locked. Close Minecraft and press Load again."; statusCol = Bad; }
        catch (Exception ex) { status = "Could not load: " + ex.Message; statusCol = Bad; }
    }

    void DoUnload() {
        if (selected < 0) return;
        var p = profiles[selected];
        try {
            int n = 0;
            if (Directory.Exists(p.Mods)) foreach (var f in Directory.GetFiles(p.Mods, "zero-client*.jar")) { File.Delete(f); n++; }
            Inspect(p);
            status = n > 0 ? "ZERO removed from " + p.Name + "." : "ZERO was not in this profile.";
            statusCol = Muted;
        } catch (IOException) { status = "mods folder is locked. Close Minecraft first."; statusCol = Bad; }
    }

    void Browse() {
        using (var d = new FolderBrowserDialog { Description = "Pick the Minecraft game folder (the one holding, or getting, a 'mods' folder)" }) {
            if (d.ShowDialog(this) != DialogResult.OK) return;
            string dir = d.SelectedPath;
            if (Path.GetFileName(dir).Equals("mods", StringComparison.OrdinalIgnoreCase)) dir = Path.GetDirectoryName(dir);
            Directory.CreateDirectory(stateDir);
            File.AppendAllText(Path.Combine(stateDir, "custom.txt"), dir + Environment.NewLine);
            Reload();
            selected = profiles.FindIndex(p => p.GameDir == dir);
            if (selected < 0) selected = 0;
            UpdateStatus();
        }
    }

    // ---------------- input

    void OnDown(object s, MouseEventArgs e) {
        if (e.Y < 56) { dragging = true; drag = e.Location; return; }
        foreach (var h in hits) if (h.R.Contains(e.Location)) { if (h.Tag >= 0) { selected = h.Tag; UpdateStatus(); } else if (h.Do != null) h.Do(); return; }
    }
    void OnMove(object s, MouseEventArgs e) {
        if (dragging) { Location = new Point(Location.X + e.X - drag.X, Location.Y + e.Y - drag.Y); return; }
        mouse = e.Location;
        int h = -1;
        for (int i = 0; i < hits.Count; i++) if (hits[i].R.Contains(e.Location)) h = i;
        hover = h;
        Cursor = h >= 0 ? Cursors.Hand : Cursors.Default;
    }
    void OnUp(object s, MouseEventArgs e) { dragging = false; }

    // ---------------- paint

    protected override void OnPaint(PaintEventArgs e) {
        var g = e.Graphics;
        g.SmoothingMode = SmoothingMode.AntiAlias;
        g.TextRenderingHint = TextRenderingHint.ClearTypeGridFit;
        hits.Clear();

        g.Clear(Void);
        // top-right glow
        using (var pth = new GraphicsPath()) {
            pth.AddEllipse(W - 280, -220, 520, 420);
            using (var pg = new PathGradientBrush(pth)) { pg.CenterColor = Color.FromArgb(70, Ember); pg.SurroundColors = new[] { Color.FromArgb(0, Ember) }; g.FillPath(pg, pth); }
        }
        // sparks
        foreach (var sp in sparks) {
            int a = (int)(120 + 110 * Math.Sin(sp.Life * 6));
            a = Clamp(a, 0, 255);
            using (var b = new SolidBrush(Color.FromArgb(a, 255, 150 + (int)(sp.R * 18), 70)))
                g.FillEllipse(b, sp.X, sp.Y, sp.R * 2, sp.R * 2);
        }

        DrawHero(g);

        // close button
        var closeR = new RectangleF(W - 44, 16, 26, 26);
        using (var p = Round(closeR, 7f)) using (var b = new SolidBrush(Color.FromArgb(150, 20, 15, 12))) g.FillPath(b, p);
        DrawCenter(g, "X", 10f, FontStyle.Bold, closeR.Contains(mouse) ? EmberHi : Muted, closeR);
        hits.Add(new Hit { R = closeR, Tag = -1, Do = Close });

        DrawText(g, "CHOOSE A PROFILE", 8.5f, FontStyle.Bold, Color.FromArgb(120, 106, 98), ListX + 2, ListY - 20);

        // list
        var listRect = new RectangleF(ListX, ListY, ListW, RowH * Rows + 6);
        using (var p = Round(listRect, 12f)) using (var b = new SolidBrush(Color.FromArgb(200, 12, 9, 8))) g.FillPath(b, p);
        var clip = g.Clip;
        using (var p = Round(listRect, 12f)) { g.SetClip(p); }
        for (int i = 0; i < profiles.Count; i++) {
            int row = i - scroll;
            if (row < 0 || row >= Rows) continue;
            var r = new RectangleF(ListX + 4, ListY + 4 + row * RowH, ListW - 8, RowH - 4);
            var pr = profiles[i];
            bool sel = i == selected;
            bool hov = r.Contains(mouse);
            if (sel) using (var p = Round(r, 9f)) using (var b = new SolidBrush(Color.FromArgb(46, 34, 26))) g.FillPath(b, p);
            else if (hov) using (var p = Round(r, 9f)) using (var b = new SolidBrush(Color.FromArgb(26, 20, 16))) g.FillPath(b, p);
            if (sel) using (var b = new SolidBrush(Ember)) { using (var p = Round(new RectangleF(r.X, r.Y + 7, 3, r.Height - 14), 1.5f)) g.FillPath(b, p); }
            DrawText(g, pr.Name, 10.5f, FontStyle.Bold, Text1, r.X + 15, r.Y + 6);
            string sub = pr.Launcher + (pr.Launcher == "NoRisk" ? "  -  API by launcher" : pr.HasApi ? "  -  Fabric API ok" : "  -  no Fabric API");
            DrawText(g, sub, 8f, FontStyle.Regular, Muted, r.X + 15, r.Y + 24);
            if (pr.HasZero) {
                var tag = new RectangleF(r.Right - 74, r.Y + 11, 62, 18);
                using (var p = Round(tag, 9f)) using (var b = new SolidBrush(Color.FromArgb(40, 126, 231, 135))) g.FillPath(b, p);
                DrawCenter(g, "LOADED", 7.5f, FontStyle.Bold, Good, tag);
            }
            hits.Add(new Hit { R = r, Tag = i });
        }
        g.Clip = clip;
        using (var pen = new Pen(Line)) using (var p = Round(listRect, 12f)) g.DrawPath(pen, p);
        if (profiles.Count > Rows) {
            float th = (RowH * Rows + 6) * (float)Rows / profiles.Count;
            float ty = ListY + 3 + (RowH * Rows) * (float)scroll / profiles.Count;
            using (var b = new SolidBrush(Color.FromArgb(90, Ember))) g.FillRectangle(b, ListX + ListW - 4, ty, 3, th);
        }

        // status line
        var sr = new RectangleF(PAD, ListY + RowH * Rows + 20, W - PAD * 2, 34);
        DrawTextWrap(g, status, 9f, statusCol, sr);

        // buttons
        float by = H - 58;
        var loadR = new RectangleF(PAD, by, 232, 42);
        DrawLoadButton(g, loadR);
        hits.Add(new Hit { R = loadR, Tag = -1, Do = DoLoad });

        float bx = PAD + 244;
        bx = Ghost(g, "Unload", bx, by, 96, DoUnload);
        bx = Ghost(g, "Folder", bx, by, 92, () => { if (selected >= 0) { Directory.CreateDirectory(profiles[selected].Mods); System.Diagnostics.Process.Start("explorer.exe", "\"" + profiles[selected].Mods + "\""); } });
        bx = Ghost(g, "Browse", bx, by, 96, Browse);

        using (var pen = new Pen(Line)) g.DrawPath(pen, Round(new RectangleF(0.5f, 0.5f, W - 1, H - 1), 16));
    }

    void DrawHero(Graphics g) {
        var hero = new Rectangle(0, 0, W, HeroH);
        // Vorschaubild als Hintergrund, oben mit runden Ecken, nach unten ausblendend
        var oldClip = g.Clip;
        using (var clipPath = new GraphicsPath()) {
            clipPath.AddArc(0, 0, 32, 32, 180, 90);
            clipPath.AddArc(W - 32, 0, 32, 32, 270, 90);
            clipPath.AddLine(W, HeroH, 0, HeroH);
            clipPath.CloseFigure();
            g.SetClip(clipPath);
            if (preview != null) {
                var ia = new System.Drawing.Imaging.ImageAttributes();
                g.DrawImage(preview, hero, 0, 0, preview.Width, preview.Height, GraphicsUnit.Pixel, ia);
            } else { using (var b = new SolidBrush(Color.FromArgb(14, 18, 30))) g.FillRectangle(b, hero); }
            // abdunkeln + nach unten in Void verlaufen
            using (var b = new SolidBrush(Color.FromArgb(120, 6, 5, 4))) g.FillRectangle(b, hero);
            using (var lg = new LinearGradientBrush(new Rectangle(0, HeroH - 120, W, 120), Color.FromArgb(0, Void), Void, 90f)) g.FillRectangle(lg, 0, HeroH - 120, W, 120);
            // Ember-Schein von links unten (wie Glut hinterm Logo)
            using (var pth = new GraphicsPath()) {
                pth.AddEllipse(-80, HeroH - 150, 360, 320);
                using (var pg = new PathGradientBrush(pth)) { pg.CenterColor = Color.FromArgb(90, Ember); pg.SurroundColors = new[] { Color.FromArgb(0, Ember) }; g.FillPath(pg, pth); }
            }
            g.Clip = oldClip;
        }
        // Wortmarke groß
        using (var logo = new Font("Georgia", 44f, FontStyle.Bold | FontStyle.Italic))
        using (var sh = new SolidBrush(Color.FromArgb(160, 0, 0, 0))) g.DrawString("ZERO", logo, sh, PAD - 2, HeroH - 118);
        using (var logo = new Font("Georgia", 44f, FontStyle.Bold | FontStyle.Italic))
        using (var br = new LinearGradientBrush(new Rectangle(PAD, HeroH - 120, 260, 76), EmberHi, Ember, 100f)) {
            g.DrawString("ZERO", logo, br, PAD - 4, HeroH - 120);
        }
        DrawText(g, "C L I E N T", 9f, FontStyle.Bold, Color.FromArgb(230, 220, 212), PAD, HeroH - 46);
        DrawText(g, "Crystal PvP  ·  Visuals  ·  HUD  ·  own render engine", 8.5f, FontStyle.Regular, Color.FromArgb(180, 168, 158), PAD, HeroH - 30);
        // Chips oben rechts
        float cx = W - PAD - 26;
        cx = Chip(g, "FABRIC 1.21.11", cx, 20);
        cx = Chip(g, "ONE CLICK", cx, 20);
    }

    float Chip(Graphics g, string label, float right, float y) {
        using (var f = new Font("Segoe UI", 8f, FontStyle.Bold)) {
            float tw = g.MeasureString(label, f).Width;
            var r = new RectangleF(right - tw - 18, y, tw + 18, 22);
            using (var p = Round(r, 11f)) { using (var b = new SolidBrush(Color.FromArgb(150, 20, 15, 12))) g.FillPath(b, p); using (var pen = new Pen(Color.FromArgb(90, Ember))) g.DrawPath(pen, p); }
            using (var b = new SolidBrush(EmberHi)) using (var sf = new StringFormat { Alignment = StringAlignment.Center, LineAlignment = StringAlignment.Center }) g.DrawString(label, f, b, r, sf);
            return r.X - 8;
        }
    }

    void DrawLoadButton(Graphics g, RectangleF r) {
        bool on = selected >= 0 && profiles.Count > 0;
        string label = on && profiles[selected].HasZero ? "RELOAD / UPDATE" : "LOAD";
        float press = Math.Max(0, pressT);
        var rr = new RectangleF(r.X + press * 1.5f, r.Y + press * 1.5f, r.Width - press * 3, r.Height - press * 3);
        using (var p = Round(rr, 11f)) {
            // outer glow
            for (int i = 3; i >= 1; i--) {
                int a = (int)(40 * loadGlow / i);
                using (var gp = Round(new RectangleF(rr.X - i * 2, rr.Y - i * 2, rr.Width + i * 4, rr.Height + i * 4), 11f + i * 2))
                using (var b = new SolidBrush(Color.FromArgb(a, Ember))) g.FillPath(b, gp);
            }
            using (var b = new LinearGradientBrush(Rectangle.Round(rr), EmberHi, on ? Ember : Color.FromArgb(90, 60, 40), 90f)) g.FillPath(b, p);
            // moving sheen
            float sx = rr.X + rr.Width * ((t * 0.3f) % 1f);
            using (var lb = new LinearGradientBrush(new RectangleF(sx - 30, rr.Y, 60, rr.Height), Color.FromArgb(0, 255, 255, 255), Color.FromArgb(60, 255, 255, 255), 0f)) {
                var old = g.Clip; g.SetClip(p); g.FillRectangle(lb, sx - 30, rr.Y, 60, rr.Height); g.Clip = old;
            }
        }
        DrawCenter(g, label, 11.5f, FontStyle.Bold, Color.FromArgb(28, 16, 8), rr);
    }

    float Ghost(Graphics g, string label, float x, float y, float w, Action act) {
        var r = new RectangleF(x, y, w, 42);
        bool hov = r.Contains(mouse);
        using (var p = Round(r, 10f)) {
            using (var b = new SolidBrush(hov ? CardHi : Card)) g.FillPath(b, p);
            using (var pen = new Pen(hov ? Ember : Line)) g.DrawPath(pen, p);
        }
        DrawCenter(g, label, 9.5f, FontStyle.Bold, hov ? EmberHi : Text1, r);
        hits.Add(new Hit { R = r, Tag = -1, Do = act });
        return x + w + 10;
    }

    void DrawText(Graphics g, string s, float sz, FontStyle st, Color c, float x, float y) {
        using (var f = new Font("Segoe UI", sz, st)) using (var b = new SolidBrush(c)) g.DrawString(s, f, b, x, y);
    }
    void DrawCenter(Graphics g, string s, float sz, FontStyle st, Color c, RectangleF r) {
        using (var f = new Font("Segoe UI", sz, st)) using (var b = new SolidBrush(c))
        using (var sf = new StringFormat { Alignment = StringAlignment.Center, LineAlignment = StringAlignment.Center })
            g.DrawString(s, f, b, r, sf);
    }
    void DrawTextWrap(Graphics g, string s, float sz, Color c, RectangleF r) {
        using (var f = new Font("Segoe UI", sz)) using (var b = new SolidBrush(c)) g.DrawString(s, f, b, r);
    }

    static int Clamp(int v, int a, int b) { return v < a ? a : v > b ? b : v; }

    protected override void OnFormClosed(FormClosedEventArgs e) { timer.Stop(); base.OnFormClosed(e); }
}
