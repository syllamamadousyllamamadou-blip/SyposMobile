import { Smartphone, Shield, BarChart3, Cloud, ChevronRight, MessageCircle } from 'lucide-react';
import './LandingPage.css';

export default function LandingPage({ onLoginClick }: { onLoginClick: () => void }) {
  const whatsappNumber = "2250574566438";
  const message = encodeURIComponent("Bonjour, je suis intéressé par l'application KAYO Mobile. J'aimerais avoir plus d'informations.");
  const whatsappUrl = `https://wa.me/${whatsappNumber}?text=${message}`;

  return (
    <div className="landing-container">
      {/* Navbar */}
      <nav className="navbar">
        <div className="nav-logo" onClick={onLoginClick}>
          <div className="logo-box">S</div>
          <span className="brand-name">KAYO <span className="brand-highlight">Mobile</span></span>
        </div>
        <div className="nav-actions">
          <button onClick={onLoginClick} className="btn-cta-secondary" style={{marginRight: '10px'}}>
            Espace Client
          </button>
          <a href={whatsappUrl} target="_blank" rel="noreferrer" className="btn-whatsapp-outline">
            <MessageCircle size={18} />
            Contact
          </a>
        </div>
      </nav>

      {/* Hero Section */}
      <section className="hero">
        <div className="hero-content">
          <div className="hero-badge">🚀 Le futur de votre point de vente</div>
          <h1 className="hero-title">Gérez votre commerce du bout des doigts.</h1>
          <p className="hero-subtitle">
            Transformez votre téléphone Android en une caisse enregistreuse puissante. 
            Suivez vos ventes en temps réel, gérez vos stocks et ne perdez plus jamais d'argent.
          </p>
          <div className="hero-cta-group">
            <a href={whatsappUrl} target="_blank" rel="noreferrer" className="btn-cta-primary">
              <MessageCircle size={20} />
              Commander sur WhatsApp
            </a>
            <button onClick={onLoginClick} className="btn-cta-secondary">
              Espace Client / Admin
              <ChevronRight size={18} />
            </button>
          </div>
          <div className="hero-stats">
            <div className="stat-item">
              <span className="stat-number">100%</span>
              <span className="stat-label">Sécurisé</span>
            </div>
            <div className="stat-divider"></div>
            <div className="stat-item">
              <span className="stat-number">24/7</span>
              <span className="stat-label">Support</span>
            </div>
            <div className="stat-divider"></div>
            <div className="stat-item">
              <span className="stat-number">Sans</span>
              <span className="stat-label">Internet requis</span>
            </div>
          </div>
        </div>
        <div className="hero-visual">
          <div className="phone-mockup">
            <div className="phone-screen">
              <div className="mock-header">Tableau de bord</div>
              <div className="mock-card">
                <div className="mock-title">Ventes du jour</div>
                <div className="mock-value">145 000 FCFA</div>
              </div>
              <div className="mock-chart">
                <div className="bar" style={{height: '40%'}}></div>
                <div className="bar" style={{height: '70%'}}></div>
                <div className="bar" style={{height: '50%'}}></div>
                <div className="bar" style={{height: '100%'}}></div>
                <div className="bar" style={{height: '80%'}}></div>
              </div>
              <div className="mock-action">Nouvelle Vente</div>
            </div>
          </div>
          <div className="glow-circle"></div>
        </div>
      </section>

      {/* Features */}
      <section className="features-section">
        <h2 className="section-title">Pourquoi choisir KAYO Mobile ?</h2>
        <div className="features-grid">
          <div className="feature-card">
            <div className="feature-icon bg-blue"><BarChart3 size={24} /></div>
            <h3>Suivi à Distance</h3>
            <p>Consultez votre chiffre d'affaires, vos bénéfices et vos dépenses depuis chez vous, en temps réel.</p>
          </div>
          <div className="feature-card">
            <div className="feature-icon bg-green"><Smartphone size={24} /></div>
            <h3>Facile et Rapide</h3>
            <p>Une interface pensée pour les caissiers. Encaissez en quelques secondes, même sans connexion internet.</p>
          </div>
          <div className="feature-card">
            <div className="feature-icon bg-purple"><Shield size={24} /></div>
            <h3>Anti-Fraude</h3>
            <p>Contrôlez vos employés. Vous pouvez bloquer ou mettre en pause l'application à tout moment à distance.</p>
          </div>
          <div className="feature-card">
            <div className="feature-icon bg-orange"><Cloud size={24} /></div>
            <h3>Sauvegarde Cloud</h3>
            <p>Toutes vos données (produits, tickets, clients) sont sauvegardées sur le Cloud de manière sécurisée.</p>
          </div>
        </div>
      </section>

      {/* CTA Bottom */}
      <section className="bottom-cta">
        <h2>Prêt à moderniser votre boutique ?</h2>
        <p>Rejoignez les gérants de commerces, maquis et boutiques qui font confiance à KAYO.</p>
        <a href={whatsappUrl} target="_blank" rel="noreferrer" className="btn-cta-primary large">
          Discutons sur WhatsApp
        </a>
      </section>

      {/* Footer */}
      <footer className="footer">
        <div className="footer-content">
          <div className="footer-brand">KAYO Mobile © {new Date().getFullYear()}</div>
          <div className="footer-links">
            <span className="footer-link">Confidentialité</span>
            <span className="footer-link">Conditions</span>
            <button onClick={onLoginClick} className="footer-link-btn">Connexion Espace Client</button>
          </div>
        </div>
      </footer>
    </div>
  );
}
