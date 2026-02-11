#!/usr/bin/env python3
"""
Nku Project Status Report Generator - February 2026
Generates 4K PNG visualizations for project overview
"""

import matplotlib.pyplot as plt
import matplotlib.patches as mpatches
from matplotlib.patches import FancyBboxPatch, Circle, FancyArrowPatch, Wedge
import numpy as np
from datetime import datetime, timedelta
import os

# Premium color palette
COLORS = {
    'bg_dark': '#0D1117',
    'bg_card': '#161B22',
    'teal': '#00D4AA',
    'teal_dim': '#00A888',
    'gold': '#FFD700',
    'orange': '#FF6B35',
    'red': '#FF4757',
    'green': '#00E676',
    'blue': '#4FC3F7',
    'purple': '#BB86FC',
    'white': '#FFFFFF',
    'gray': '#8B949E',
    'gray_dark': '#30363D',
}

def setup_dark_figure(width=16, height=9, dpi=250):
    """Create a dark-themed 4K figure"""
    fig = plt.figure(figsize=(width, height), dpi=dpi, facecolor=COLORS['bg_dark'])
    return fig

def add_glass_card(ax, x, y, width, height, alpha=0.15, edge_color=None):
    """Add a glassmorphism card effect"""
    card = FancyBboxPatch(
        (x, y), width, height,
        boxstyle="round,pad=0.02,rounding_size=0.02",
        facecolor=COLORS['bg_card'],
        edgecolor=edge_color or COLORS['teal'],
        linewidth=2,
        alpha=0.9
    )
    ax.add_patch(card)
    return card

def generate_highlevel_architecture():
    """Generate high-level system architecture diagram"""
    fig = setup_dark_figure(16, 10)
    ax = fig.add_axes([0.02, 0.02, 0.96, 0.96])
    ax.set_xlim(0, 1)
    ax.set_ylim(0, 1)
    ax.set_facecolor(COLORS['bg_dark'])
    ax.axis('off')
    
    # Title
    ax.text(0.5, 0.95, 'NKU CLINICAL AI ARCHITECTURE', 
            fontsize=28, fontweight='bold', color=COLORS['teal'],
            ha='center', va='top', fontfamily='sans-serif')
    ax.text(0.5, 0.90, 'Offline-First Medical Triage for Pan-Africa',
            fontsize=14, color=COLORS['gray'], ha='center', va='top')
    
    # User Input Layer
    add_glass_card(ax, 0.05, 0.70, 0.20, 0.15, edge_color=COLORS['blue'])
    ax.text(0.15, 0.82, '📱 USER INPUT', fontsize=11, fontweight='bold', 
            color=COLORS['blue'], ha='center')
    ax.text(0.15, 0.77, 'Text / Voice (47 Languages)', fontsize=9, 
            color=COLORS['gray'], ha='center')
    ax.text(0.15, 0.73, 'Twi • Yoruba • Hausa • Swahili', fontsize=8, 
            color=COLORS['gray'], ha='center', style='italic')
    
    # Nku Cycle Core
    add_glass_card(ax, 0.30, 0.55, 0.40, 0.30, edge_color=COLORS['teal'])
    ax.text(0.50, 0.82, '⚡ NKU CYCLE', fontsize=14, fontweight='bold', 
            color=COLORS['teal'], ha='center')
    
    # TranslateGemma
    add_glass_card(ax, 0.33, 0.68, 0.14, 0.10, edge_color=COLORS['purple'])
    ax.text(0.40, 0.75, 'TranslateGemma', fontsize=9, fontweight='bold', 
            color=COLORS['purple'], ha='center')
    ax.text(0.40, 0.71, '4B IQ1_M (0.51GB)', fontsize=8, 
            color=COLORS['gray'], ha='center')
    
    # MedGemma
    add_glass_card(ax, 0.53, 0.68, 0.14, 0.10, edge_color=COLORS['gold'])
    ax.text(0.60, 0.75, 'MedGemma', fontsize=9, fontweight='bold', 
            color=COLORS['gold'], ha='center')
    ax.text(0.60, 0.71, '4B IQ1_M (0.78GB)', fontsize=8, 
            color=COLORS['gray'], ha='center')
    
    # llama.cpp JNI
    add_glass_card(ax, 0.38, 0.57, 0.24, 0.08, edge_color=COLORS['green'])
    ax.text(0.50, 0.62, 'llama.cpp JNI (SmolLM)', fontsize=9, fontweight='bold', 
            color=COLORS['green'], ha='center')
    ax.text(0.50, 0.59, 'NDK 29 • ARM64/x86_64', fontsize=8, 
            color=COLORS['gray'], ha='center')
    
    # Android System TTS Output
    add_glass_card(ax, 0.75, 0.70, 0.20, 0.15, edge_color=COLORS['orange'])
    ax.text(0.85, 0.82, '🔊 Android System TTS', fontsize=11, fontweight='bold', 
            color=COLORS['orange'], ha='center')
    ax.text(0.85, 0.77, 'Offline Voice Output', fontsize=9, 
            color=COLORS['gray'], ha='center')
    ax.text(0.85, 0.73, '~20MB per voice', fontsize=8, 
            color=COLORS['gray'], ha='center', style='italic')
    
    # Flow arrows
    arrow_style = dict(arrowstyle='->', color=COLORS['teal'], lw=2, 
                       connectionstyle='arc3,rad=0')
    ax.annotate('', xy=(0.30, 0.77), xytext=(0.25, 0.77), arrowprops=arrow_style)
    ax.annotate('', xy=(0.75, 0.77), xytext=(0.70, 0.77), arrowprops=arrow_style)
    
    # Target Device
    add_glass_card(ax, 0.30, 0.25, 0.40, 0.22, edge_color=COLORS['teal_dim'])
    ax.text(0.50, 0.44, '📲 TARGET DEVICE', fontsize=12, fontweight='bold', 
            color=COLORS['teal'], ha='center')
    
    specs = [
        ('RAM', '2GB - 4GB', COLORS['green']),
        ('Storage', '4GB minimum', COLORS['blue']),
        ('Network', '100% Offline', COLORS['gold']),
        ('Models', '~1.3GB total', COLORS['purple']),
    ]
    for i, (label, value, color) in enumerate(specs):
        y_pos = 0.38 - i * 0.035
        ax.text(0.35, y_pos, label + ':', fontsize=9, color=COLORS['gray'], ha='left')
        ax.text(0.50, y_pos, value, fontsize=9, fontweight='bold', color=color, ha='left')
    
    # Cloud Fallback
    add_glass_card(ax, 0.75, 0.25, 0.20, 0.22, edge_color=COLORS['gray_dark'])
    ax.text(0.85, 0.44, '☁️ CLOUD FALLBACK', fontsize=10, fontweight='bold', 
            color=COLORS['gray'], ha='center')
    ax.text(0.85, 0.38, 'Google Cloud Run', fontsize=9, 
            color=COLORS['gray'], ha='center')
    ax.text(0.85, 0.34, 'Emulator / Dev Only', fontsize=8, 
            color=COLORS['gray'], ha='center', style='italic')
    ax.text(0.85, 0.29, 'NOT for production', fontsize=8, 
            color=COLORS['red'], ha='center')
    
    # Impact Stats
    add_glass_card(ax, 0.05, 0.25, 0.20, 0.38, edge_color=COLORS['gold'])
    ax.text(0.15, 0.60, '🌍 IMPACT', fontsize=12, fontweight='bold', 
            color=COLORS['gold'], ha='center')
    
    impact_stats = [
        ('450M+', 'Target Population'),
        ('47', 'Languages'),
        ('$50-100', 'Device Cost'),
        ('0', 'Network Required'),
        ('14+', 'African Dialects'),
    ]
    for i, (value, label) in enumerate(impact_stats):
        y_pos = 0.52 - i * 0.055
        ax.text(0.15, y_pos, value, fontsize=14, fontweight='bold', 
                color=COLORS['teal'], ha='center')
        ax.text(0.15, y_pos - 0.02, label, fontsize=8, 
                color=COLORS['gray'], ha='center')
    
    # Footer
    ax.text(0.5, 0.05, 'NKU • MedGemma Impact Challenge 2026 • Edge AI Prize Track',
            fontsize=10, color=COLORS['gray'], ha='center', style='italic')
    ax.text(0.98, 0.02, f'Generated: {datetime.now().strftime("%Y-%m-%d %H:%M")}',
            fontsize=8, color=COLORS['gray_dark'], ha='right')
    
    return fig

def generate_status_dashboard():
    """Generate detailed project status dashboard"""
    fig = setup_dark_figure(18, 12)
    ax = fig.add_axes([0.02, 0.02, 0.96, 0.96])
    ax.set_xlim(0, 1)
    ax.set_ylim(0, 1)
    ax.set_facecolor(COLORS['bg_dark'])
    ax.axis('off')
    
    # Title
    ax.text(0.5, 0.97, 'NKU PROJECT STATUS DASHBOARD', 
            fontsize=28, fontweight='bold', color=COLORS['teal'],
            ha='center', va='top')
    ax.text(0.5, 0.93, f'February 4, 2026 | 20 Days to Deadline',
            fontsize=12, color=COLORS['gray'], ha='center', va='top')
    
    # Competition Score Card
    add_glass_card(ax, 0.02, 0.72, 0.30, 0.18, edge_color=COLORS['gold'])
    ax.text(0.17, 0.87, '🏆 COMPETITION READINESS', fontsize=12, fontweight='bold', 
            color=COLORS['gold'], ha='center')
    
    # Score gauge
    score = 78
    theta1, theta2 = 180, 180 - (score / 100) * 180
    wedge_bg = Wedge((0.17, 0.78), 0.06, 0, 180, width=0.015, 
                     facecolor=COLORS['gray_dark'], edgecolor='none')
    wedge_score = Wedge((0.17, 0.78), 0.06, theta2, 180, width=0.015, 
                        facecolor=COLORS['teal'], edgecolor='none')
    ax.add_patch(wedge_bg)
    ax.add_patch(wedge_score)
    ax.text(0.17, 0.77, f'{score}/100', fontsize=18, fontweight='bold', 
            color=COLORS['teal'], ha='center', va='center')
    ax.text(0.17, 0.74, 'Target: 92+', fontsize=9, color=COLORS['gray'], ha='center')
    
    # Phase Completion
    add_glass_card(ax, 0.34, 0.72, 0.32, 0.18, edge_color=COLORS['green'])
    ax.text(0.50, 0.87, '📊 PHASE COMPLETION', fontsize=12, fontweight='bold', 
            color=COLORS['green'], ha='center')
    
    phases = [
        ('Model Acquisition', 100, COLORS['green']),
        ('GGUF Quantization', 100, COLORS['green']),
        ('Android Integration', 100, COLORS['green']),
        ('Localization', 100, COLORS['green']),
        ('Video Demo', 0, COLORS['red']),
        ('Kaggle Writeup', 30, COLORS['orange']),
    ]
    
    bar_width = 0.04
    for i, (phase, pct, color) in enumerate(phases):
        y = 0.84 - i * 0.02
        # Background bar
        ax.add_patch(FancyBboxPatch((0.36, y - 0.006), 0.20, 0.012,
                     boxstyle="round,pad=0.001,rounding_size=0.005",
                     facecolor=COLORS['gray_dark'], edgecolor='none'))
        # Progress bar
        if pct > 0:
            ax.add_patch(FancyBboxPatch((0.36, y - 0.006), 0.20 * (pct/100), 0.012,
                         boxstyle="round,pad=0.001,rounding_size=0.005",
                         facecolor=color, edgecolor='none'))
        ax.text(0.355, y, phase, fontsize=7, color=COLORS['white'], ha='right', va='center')
        ax.text(0.57, y, f'{pct}%', fontsize=7, color=color, ha='left', va='center')
    
    # Timeline
    add_glass_card(ax, 0.68, 0.72, 0.30, 0.18, edge_color=COLORS['blue'])
    ax.text(0.83, 0.87, '📅 TIMELINE', fontsize=12, fontweight='bold', 
            color=COLORS['blue'], ha='center')
    
    timeline = [
        ('Feb 4', 'Today', COLORS['teal'], True),
        ('Feb 10', 'Video Due', COLORS['orange'], False),
        ('Feb 17', 'Writeup Due', COLORS['orange'], False),
        ('Feb 24', 'SUBMISSION', COLORS['red'], False),
    ]
    
    for i, (date, label, color, is_current) in enumerate(timeline):
        x = 0.71 + i * 0.075
        # Timeline dot
        circle = Circle((x, 0.80), 0.008, facecolor=color, edgecolor=COLORS['white'], 
                        linewidth=2 if is_current else 1)
        ax.add_patch(circle)
        ax.text(x, 0.77, date, fontsize=8, color=color, ha='center', fontweight='bold')
        ax.text(x, 0.75, label, fontsize=7, color=COLORS['gray'], ha='center')
    # Timeline line
    ax.plot([0.71, 0.935], [0.80, 0.80], color=COLORS['gray_dark'], lw=2, zorder=0)
    
    # Technical Stack
    add_glass_card(ax, 0.02, 0.38, 0.30, 0.32, edge_color=COLORS['purple'])
    ax.text(0.17, 0.67, '🔧 TECHNICAL STACK', fontsize=12, fontweight='bold', 
            color=COLORS['purple'], ha='center')
    
    stack = [
        ('MedGemma 4B', 'IQ1_M • 0.78GB', COLORS['gold']),
        ('TranslateGemma 4B', 'IQ1_M • 0.51GB', COLORS['purple']),
        ('Inference Engine', 'llama.cpp JNI', COLORS['green']),
        ('TTS', 'Android System TTS • 0 MB', COLORS['orange']),
        ('Android SDK', '35 • Kotlin 2.1.0', COLORS['blue']),
        ('NDK', '29.0.13113456', COLORS['teal']),
        ('APK Size', '2.7GB (debug)', COLORS['gray']),
        ('Total Models', '~1.3GB', COLORS['teal']),
    ]
    
    for i, (component, spec, color) in enumerate(stack):
        y = 0.63 - i * 0.032
        ax.text(0.04, y, component, fontsize=9, color=COLORS['white'], ha='left')
        ax.text(0.30, y, spec, fontsize=8, color=color, ha='right')
    
    # Evaluation Criteria
    add_glass_card(ax, 0.34, 0.38, 0.32, 0.32, edge_color=COLORS['teal'])
    ax.text(0.50, 0.67, '📋 EVALUATION CRITERIA', fontsize=12, fontweight='bold', 
            color=COLORS['teal'], ha='center')
    
    criteria = [
        ('Execution & Comm.', 30, 6, 10, COLORS['red']),
        ('HAI-DEF Usage', 20, 9, 10, COLORS['green']),
        ('Product Feasibility', 20, 8, 10, COLORS['green']),
        ('Problem Domain', 15, 9, 10, COLORS['green']),
        ('Impact Potential', 15, 9, 10, COLORS['green']),
    ]
    
    for i, (name, weight, score, max_score, color) in enumerate(criteria):
        y = 0.62 - i * 0.048
        ax.text(0.36, y, name, fontsize=9, color=COLORS['white'], ha='left')
        ax.text(0.51, y, f'{weight}%', fontsize=8, color=COLORS['gray'], ha='center')
        # Score bar
        bar_x = 0.54
        bar_len = 0.10
        ax.add_patch(FancyBboxPatch((bar_x, y - 0.008), bar_len, 0.016,
                     boxstyle="round,pad=0.001,rounding_size=0.005",
                     facecolor=COLORS['gray_dark'], edgecolor='none'))
        ax.add_patch(FancyBboxPatch((bar_x, y - 0.008), bar_len * (score/max_score), 0.016,
                     boxstyle="round,pad=0.001,rounding_size=0.005",
                     facecolor=color, edgecolor='none'))
        ax.text(0.65, y, f'{score}/{max_score}', fontsize=8, color=color, ha='left')
    
    # Language Coverage
    add_glass_card(ax, 0.68, 0.38, 0.30, 0.32, edge_color=COLORS['orange'])
    ax.text(0.83, 0.67, '🌍 LANGUAGE COVERAGE', fontsize=12, fontweight='bold', 
            color=COLORS['orange'], ha='center')
    
    languages = [
        ('Core (Verified)', 'English, Twi, Yoruba, Hausa, Swahili, Ewe, Ga', COLORS['green']),
        ('Extended', 'French, Portuguese, Amharic, Zulu, Igbo...', COLORS['blue']),
        ('Total', '47 Languages', COLORS['teal']),
    ]
    
    y = 0.62
    for category, langs, color in languages:
        ax.text(0.70, y, category + ':', fontsize=9, fontweight='bold', color=color, ha='left')
        y -= 0.025
        ax.text(0.70, y, langs, fontsize=8, color=COLORS['gray'], ha='left')
        y -= 0.04
    
    # Verified Test Results
    ax.text(0.83, 0.48, 'Verified Triage Tests:', fontsize=9, 
            color=COLORS['white'], ha='center', fontweight='bold')
    tests = ['✅ Twi → Malaria', '✅ Yoruba → Gastro', '✅ Hausa → Malaria', 
             '✅ Swahili → Pneumonia', '✅ English → Dehydration']
    for i, test in enumerate(tests):
        ax.text(0.83, 0.45 - i * 0.022, test, fontsize=7, 
                color=COLORS['green'], ha='center')
    
    # Critical Blockers
    add_glass_card(ax, 0.02, 0.08, 0.47, 0.28, edge_color=COLORS['red'])
    ax.text(0.255, 0.33, '⚠️ CRITICAL BLOCKERS', fontsize=12, fontweight='bold', 
            color=COLORS['red'], ha='center')
    
    blockers = [
        ('🔴 Video Demo (3 min)', 'NOT STARTED - Required for 30% of score'),
        ('🟡 Kaggle Writeup', 'Draft exists - Needs finalization'),
        ('🟡 Code Repository', 'Needs cleanup for public review'),
    ]
    
    y = 0.28
    for title, desc in blockers:
        ax.text(0.04, y, title, fontsize=10, fontweight='bold', 
                color=COLORS['white'], ha='left')
        ax.text(0.04, y - 0.022, desc, fontsize=8, 
                color=COLORS['gray'], ha='left')
        y -= 0.06
    
    # Next Actions
    add_glass_card(ax, 0.51, 0.08, 0.47, 0.28, edge_color=COLORS['green'])
    ax.text(0.745, 0.33, '🚀 NEXT ACTIONS', fontsize=12, fontweight='bold', 
            color=COLORS['green'], ha='center')
    
    actions = [
        ('1️⃣ Record Video Demo', 'Show Nku Cycle on physical device'),
        ('2️⃣ Finalize Writeup', 'Technical paper (3 pages max)'),
        ('3️⃣ Clean Repository', 'Document code, update README'),
        ('4️⃣ Submit to Kaggle', 'Edge AI Prize track selection'),
    ]
    
    y = 0.28
    for title, desc in actions:
        ax.text(0.53, y, title, fontsize=10, fontweight='bold', 
                color=COLORS['white'], ha='left')
        ax.text(0.53, y - 0.022, desc, fontsize=8, 
                color=COLORS['gray'], ha='left')
        y -= 0.055
    
    # Footer
    ax.text(0.5, 0.02, 'NKU • MedGemma Impact Challenge 2026 • Target: Edge AI Prize ($5,000)',
            fontsize=10, color=COLORS['gray'], ha='center', style='italic')
    
    return fig

def generate_timeline():
    """Generate project timeline visualization"""
    fig = setup_dark_figure(18, 8)
    ax = fig.add_axes([0.04, 0.15, 0.92, 0.75])
    ax.set_xlim(0, 1)
    ax.set_ylim(0, 1)
    ax.set_facecolor(COLORS['bg_dark'])
    ax.axis('off')
    
    # Title
    ax.text(0.5, 0.95, 'NKU PROJECT TIMELINE', 
            fontsize=24, fontweight='bold', color=COLORS['teal'],
            ha='center', va='top')
    ax.text(0.5, 0.88, 'Path to MedGemma Impact Challenge Submission',
            fontsize=12, color=COLORS['gray'], ha='center', va='top')
    
    # Timeline base
    ax.plot([0.05, 0.95], [0.50, 0.50], color=COLORS['gray_dark'], lw=4, zorder=0)
    
    # Milestones
    milestones = [
        (0.08, 'Jan 2026', 'Model Acquisition', ['MedGemma 4B', 'TranslateGemma 4B'], COLORS['green'], True),
        (0.20, 'Jan 2026', 'GGUF Conversion', ['IQ1_M Quantization', '64-chunk imatrix'], COLORS['green'], True),
        (0.32, 'Jan 2026', 'Android Build', ['llama.cpp JNI', 'SmolLM Module'], COLORS['green'], True),
        (0.44, 'Feb 2026', 'Integration', ['System TTS', '46 Languages'], COLORS['green'], True),
        (0.56, 'Feb 4', 'TODAY', ['Planning', 'Status Review'], COLORS['teal'], True),
        (0.68, 'Feb 10', 'Video Demo', ['3-min Recording', 'Nku Cycle Demo'], COLORS['orange'], False),
        (0.80, 'Feb 17', 'Writeup', ['Technical Paper', 'Code Cleanup'], COLORS['orange'], False),
        (0.92, 'Feb 24', 'SUBMISSION', ['Kaggle Submit', 'Edge AI Prize'], COLORS['gold'], False),
    ]
    
    for i, (x, date, title, items, color, completed) in enumerate(milestones):
        # Milestone dot
        dot_size = 0.025 if title in ['TODAY', 'SUBMISSION'] else 0.018
        circle = Circle((x, 0.50), dot_size, 
                        facecolor=color if completed else COLORS['bg_dark'], 
                        edgecolor=color, linewidth=3)
        ax.add_patch(circle)
        
        # Alternate above/below
        if i % 2 == 0:
            # Above
            ax.text(x, 0.58, date, fontsize=9, color=COLORS['gray'], ha='center')
            ax.text(x, 0.64, title, fontsize=11, fontweight='bold', color=color, ha='center')
            for j, item in enumerate(items):
                ax.text(x, 0.70 + j * 0.05, f'• {item}', fontsize=8, 
                       color=COLORS['gray'], ha='center')
            ax.plot([x, x], [0.52, 0.57], color=color, lw=2)
        else:
            # Below
            ax.text(x, 0.42, date, fontsize=9, color=COLORS['gray'], ha='center')
            ax.text(x, 0.36, title, fontsize=11, fontweight='bold', color=color, ha='center')
            for j, item in enumerate(items):
                ax.text(x, 0.30 - j * 0.05, f'• {item}', fontsize=8, 
                       color=COLORS['gray'], ha='center')
            ax.plot([x, x], [0.48, 0.43], color=color, lw=2)
    
    # Progress indicator
    progress_x = 0.56  # TODAY position
    ax.add_patch(FancyBboxPatch((0.05, 0.495), progress_x - 0.05, 0.01,
                 boxstyle="round,pad=0.001,rounding_size=0.005",
                 facecolor=COLORS['teal'], edgecolor='none', alpha=0.5))
    
    # Legend
    ax.text(0.05, 0.10, '●', fontsize=14, color=COLORS['green'])
    ax.text(0.08, 0.10, 'Completed', fontsize=10, color=COLORS['gray'])
    ax.text(0.20, 0.10, '○', fontsize=14, color=COLORS['orange'])
    ax.text(0.23, 0.10, 'Pending', fontsize=10, color=COLORS['gray'])
    ax.text(0.35, 0.10, '●', fontsize=14, color=COLORS['teal'])
    ax.text(0.38, 0.10, 'Current', fontsize=10, color=COLORS['gray'])
    
    return fig

def main():
    """Generate all visualizations"""
    output_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    
    print("🎨 Generating Nku Project Status Visuals (4K)...")
    
    # High-level architecture
    print("  📐 Architecture diagram...")
    fig1 = generate_highlevel_architecture()
    fig1.savefig(os.path.join(output_dir, 'nku_architecture_feb2026_4k.png'), 
                 dpi=250, facecolor=COLORS['bg_dark'], 
                 bbox_inches='tight', pad_inches=0.1)
    plt.close(fig1)
    
    # Detailed status dashboard
    print("  📊 Status dashboard...")
    fig2 = generate_status_dashboard()
    fig2.savefig(os.path.join(output_dir, 'nku_status_feb2026_4k.png'), 
                 dpi=250, facecolor=COLORS['bg_dark'], 
                 bbox_inches='tight', pad_inches=0.1)
    plt.close(fig2)
    
    # Timeline
    print("  📅 Timeline...")
    fig3 = generate_timeline()
    fig3.savefig(os.path.join(output_dir, 'nku_timeline_feb2026_4k.png'), 
                 dpi=250, facecolor=COLORS['bg_dark'], 
                 bbox_inches='tight', pad_inches=0.1)
    plt.close(fig3)
    
    print("\n✅ All visuals generated successfully!")
    print(f"   📁 Output directory: {output_dir}")
    print("   📄 Files created:")
    print("      • nku_architecture_feb2026_4k.png")
    print("      • nku_status_feb2026_4k.png")
    print("      • nku_timeline_feb2026_4k.png")

if __name__ == '__main__':
    main()
