interface NodeType {
    x: number;
    y: number;
    vx: number;
    vy: number;
}

interface OscillatorOptions {
    phase?: number;
    offset?: number;
    frequency?: number;
    amplitude?: number;
}

interface LineOptions {
    spring: number;
}

interface ConfigType {
    friction: number;
    trails: number;
    size: number;
    dampening: number;
    tension: number;
}

class Oscillator {
    phase: number;
    offset: number;
    frequency: number;
    amplitude: number;
    value: number;

    constructor(options: OscillatorOptions = {}) {
        this.phase = options.phase || 0;
        this.offset = options.offset || 0;
        this.frequency = options.frequency || 0.001;
        this.amplitude = options.amplitude || 1;
        this.value = 0;
    }

    update(): number {
        this.phase += this.frequency;
        this.value = this.offset + Math.sin(this.phase) * this.amplitude;
        return this.value;
    }
}

class Node implements NodeType {
    x: number;
    y: number;
    vx: number;
    vy: number;

    constructor() {
        this.x = 0;
        this.y = 0;
        this.vx = 0;
        this.vy = 0;
    }
}

class Line {
    spring: number;
    friction: number;
    nodes: Node[];

    constructor(options: LineOptions, config: ConfigType, pos: { x: number; y: number }) {
        this.spring = options.spring + 0.1 * Math.random() - 0.05;
        this.friction = config.friction + 0.01 * Math.random() - 0.005;
        this.nodes = [];

        for (let i = 0; i < config.size; i++) {
            const node = new Node();
            node.x = pos.x;
            node.y = pos.y;
            this.nodes.push(node);
        }
    }

    update(pos: { x: number; y: number }, config: ConfigType) {
        let spring = this.spring;
        const firstNode = this.nodes[0];
        if (!firstNode) return;

        firstNode.vx += (pos.x - firstNode.x) * spring;
        firstNode.vy += (pos.y - firstNode.y) * spring;

        for (let i = 0; i < this.nodes.length; i++) {
            const node = this.nodes[i];
            if (!node) continue;

            if (i > 0) {
                const prev = this.nodes[i - 1];
                if (!prev) continue;
                node.vx += (prev.x - node.x) * spring;
                node.vy += (prev.y - node.y) * spring;
                node.vx += prev.vx * config.dampening;
                node.vy += prev.vy * config.dampening;
            }

            node.vx *= this.friction;
            node.vy *= this.friction;
            node.x += node.vx;
            node.y += node.vy;
            spring *= config.tension;
        }
    }

    draw(ctx: CanvasRenderingContext2D) {
        const firstNode = this.nodes[0];
        if (!firstNode) return;

        let x = firstNode.x;
        let y = firstNode.y;

        ctx.beginPath();
        ctx.moveTo(x, y);

        for (let i = 1; i < this.nodes.length - 2; i++) {
            const node = this.nodes[i];
            const next = this.nodes[i + 1];
            if (!node || !next) continue;
            x = 0.5 * (node.x + next.x);
            y = 0.5 * (node.y + next.y);
            ctx.quadraticCurveTo(node.x, node.y, x, y);
        }

        const node = this.nodes[this.nodes.length - 2];
        const next = this.nodes[this.nodes.length - 1];
        if (node && next) {
            ctx.quadraticCurveTo(node.x, node.y, next.x, next.y);
        }
        ctx.stroke();
        ctx.closePath();
    }
}

export class CursorTrail {
    private canvas: HTMLCanvasElement;
    private ctx: CanvasRenderingContext2D;
    private lines: Line[] = [];
    private pos = { x: 0, y: 0 };
    private running = false;
    private oscillator: Oscillator;
    private config: ConfigType = {
        friction: 0.5,
        trails: 20,
        size: 50,
        dampening: 0.025,
        tension: 0.99,
    };

    constructor(canvas: HTMLCanvasElement) {
        this.canvas = canvas;
        const context = canvas.getContext('2d');
        if (!context) {
            throw new Error('Canvas context not available');
        }
        this.ctx = context;

        this.oscillator = new Oscillator({
            phase: Math.random() * 2 * Math.PI,
            amplitude: 85,
            frequency: 0.0015,
            offset: 285,
        });

        this.initLines();
        this.setupEventListeners();
        this.resizeCanvas();
    }

    private initLines() {
        this.lines = [];
        for (let i = 0; i < this.config.trails; i++) {
            this.lines.push(
                new Line(
                    { spring: 0.45 + (i / this.config.trails) * 0.025 },
                    this.config,
                    this.pos
                )
            );
        }
    }

    private handleMouseMove = (e: MouseEvent | TouchEvent) => {
        if (e instanceof TouchEvent) {
            const firstTouch = e.touches.item(0);
            if (!firstTouch) return;

            this.pos.x = firstTouch.pageX;
            this.pos.y = firstTouch.pageY;
        } else {
            this.pos.x = e.clientX;
            this.pos.y = e.clientY;
        }
    };

    private setupEventListeners() {
        document.addEventListener('mousemove', this.handleMouseMove);
        document.addEventListener('touchmove', this.handleMouseMove);
        document.addEventListener('touchstart', this.handleMouseMove);
        window.addEventListener('resize', () => this.resizeCanvas());
        window.addEventListener('focus', () => {
            if (!this.running) {
                this.running = true;
                this.render();
            }
        });
    }

    private resizeCanvas() {
        this.canvas.width = window.innerWidth;
        this.canvas.height = window.innerHeight;
    }

    private render = () => {
        if (!this.running) return;

        this.ctx.globalCompositeOperation = 'source-over';
        this.ctx.clearRect(0, 0, this.canvas.width, this.canvas.height);
        this.ctx.globalCompositeOperation = 'lighter';
        // 테마 색상인 Rose-600(#e11d48) 계열의 빨간색으로 고정
        this.ctx.strokeStyle = 'rgba(225, 29, 72, 0.15)';
        this.ctx.lineWidth = 1;

        for (let i = 0; i < this.config.trails; i++) {
            const line = this.lines[i];
            if (!line) continue;
            line.update(this.pos, this.config);
            line.draw(this.ctx);
        }

        window.requestAnimationFrame(this.render);
    };

    start() {
        this.running = true;
        this.render();
    }

    stop() {
        this.running = false;
    }

    destroy() {
        this.stop();
        document.removeEventListener('mousemove', this.handleMouseMove);
        document.removeEventListener('touchmove', this.handleMouseMove);
        document.removeEventListener('touchstart', this.handleMouseMove);
    }
}
