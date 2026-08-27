/**
 * SYPOS Mobile Pro — iOS Web PWA Engine v2.2
 * 100% Offline IndexedDB + Web Bluetooth Thermal ESC/POS Driver + Camera Barcode Scanner
 */

// ==========================================
// 1. DEFAULT DATA & STATE
// ==========================================
const STATE = {
  products: [],
  categories: [],
  tickets: [],
  customers: [],
  expenses: [],
  promoCodes: [],
  heldSales: [],
  settings: {
    shopName: "SYPOS COMMERCE",
    shopAddress: "Abidjan, Côte d'Ivoire",
    shopPhone: "+225 07 58 24 55 30",
    receiptFooter: "Merci de votre visite et à bientôt !",
    sellerName: "Vendeur 1",
    showPublisherSignature: true,
    publisherSignatureText: "Solution: SYPOS MOBILE 0758245530",
    taxEnabled: false,
    taxRatePercent: 18.0,
    adminPin: "1234",
    allowNegativeStock: false,
    printerWidth: "58",
    isLicensed: false,
    licenseKey: "",
    licenseType: "Non activé",
    licenseExpiryDate: 0
  },
  cart: [],
  appliedPromo: null,
  activeCategory: "",
  html5QrCode: null,
  pendingPinAction: null,
  bleDevice: null,
  bleCharacteristic: null
};

// ==========================================
// 2. LOCAL PERSISTENCE (LocalStorage / IndexedDB)
// ==========================================
function loadFromStorage() {
  const s = localStorage.getItem('sypos_data_v2');
  if (s) {
    try {
      const parsed = JSON.parse(s);
      Object.assign(STATE.settings, parsed.settings || {});
      STATE.products = parsed.products || [];
      STATE.categories = parsed.categories || [];
      STATE.tickets = parsed.tickets || [];
      STATE.customers = parsed.customers || [];
      STATE.expenses = parsed.expenses || [];
      STATE.promoCodes = parsed.promoCodes || [];
      STATE.heldSales = parsed.heldSales || [];
    } catch (e) {
      console.error(e);
    }
  } else {
    // Seed initial dummy data
    STATE.categories = [
      { id: "1", name: "Alimentation", color: "#10B981" },
      { id: "2", name: "Boissons", color: "#3B82F6" },
      { id: "3", name: "Hygiène", color: "#EC4899" },
      { id: "4", name: "Divers", color: "#8B5CF6" }
    ];
    STATE.products = [
      { id: "p1", name: "Riz Parfumé 5kg", salePrice: 4500, costPrice: 3800, stock: 25, barcode: "61811001", catId: "1" },
      { id: "p2", name: "Huile Dinor 1L", salePrice: 1300, costPrice: 1100, stock: 40, barcode: "61811002", catId: "1" },
      { id: "p3", name: "Coca Cola 33cl", salePrice: 500, costPrice: 350, stock: 60, barcode: "61811003", catId: "2" },
      { id: "p4", name: "Eau Awa 1.5L", salePrice: 400, costPrice: 280, stock: 80, barcode: "61811004", catId: "2" },
      { id: "p5", name: "Savon Lux 150g", salePrice: 500, costPrice: 350, stock: 50, barcode: "61811005", catId: "3" },
      { id: "p6", name: "Lait Bonnet Rouge", salePrice: 700, costPrice: 550, stock: 35, barcode: "61811006", catId: "1" }
    ];
    STATE.customers = [
      { id: "c1", name: "Client Comptant", phone: "", debt: 0 },
      { id: "c2", name: "M. Kouamé", phone: "0708091011", debt: 2500 },
      { id: "c3", name: "Mme Traoré", phone: "0506070809", debt: 0 }
    ];
    STATE.promoCodes = [
      { id: "pr1", code: "SOLDES10", discountPercent: 10, isActive: true }
    ];
    saveToStorage();
  }
}

function saveToStorage() {
  localStorage.setItem('sypos_data_v2', JSON.stringify({
    settings: STATE.settings,
    products: STATE.products,
    categories: STATE.categories,
    tickets: STATE.tickets,
    customers: STATE.customers,
    expenses: STATE.expenses,
    promoCodes: STATE.promoCodes,
    heldSales: STATE.heldSales
  }));
}

// ==========================================
// 3. TAB NAVIGATION
// ==========================================
function initTabs() {
  document.querySelectorAll('.app-tabbar .tab-item').forEach(tab => {
    tab.addEventListener('click', (e) => {
      e.preventDefault();
      const tabName = tab.dataset.tab;
      document.querySelectorAll('.app-tabbar .tab-item').forEach(t => t.classList.remove('active'));
      tab.classList.add('active');

      document.querySelectorAll('.view-section').forEach(sec => sec.classList.remove('active'));
      const activeSec = document.getElementById(`view-${tabName}`);
      if (activeSec) activeSec.classList.add('active');

      // Refresh view contents
      if (tabName === 'pos') renderPos();
      else if (tabName === 'products') renderProducts();
      else if (tabName === 'history') renderHistory();
      else if (tabName === 'reports') renderReports();
      else if (tabName === 'customers') renderCustomers();
      else if (tabName === 'tools') renderTools();
      else if (tabName === 'settings') renderSettings();
    });
  });
}

// ==========================================
// 4. POS & CART LOGIC
// ==========================================
function renderPos() {
  const catScroll = document.getElementById('posCategoryScroll');
  catScroll.innerHTML = `<button class="cat-pill ${STATE.activeCategory === '' ? 'active' : ''}" data-cat="">Tous</button>`;
  STATE.categories.forEach(c => {
    catScroll.innerHTML += `<button class="cat-pill ${STATE.activeCategory === c.id ? 'active' : ''}" data-cat="${c.id}">${c.name}</button>`;
  });

  catScroll.querySelectorAll('.cat-pill').forEach(pill => {
    pill.addEventListener('click', () => {
      STATE.activeCategory = pill.dataset.cat;
      renderPos();
    });
  });

  const searchVal = document.getElementById('posSearchInput').value.toLowerCase().trim();
  const grid = document.getElementById('posProductGrid');
  grid.innerHTML = '';

  const filtered = STATE.products.filter(p => {
    const matchCat = !STATE.activeCategory || p.catId === STATE.activeCategory;
    const matchSearch = !searchVal || p.name.toLowerCase().includes(searchVal) || (p.barcode && p.barcode.includes(searchVal));
    return matchCat && matchSearch;
  });

  filtered.forEach(p => {
    const card = document.createElement('div');
    card.className = 'product-card';
    const isLow = p.stock <= 5;
    card.innerHTML = `
      <span class="stock-badge ${isLow ? 'stock-low' : 'stock-ok'}">${p.stock}</span>
      <div class="product-name">${p.name}</div>
      <div class="product-price">${p.salePrice.toLocaleString('fr-FR')} CFA</div>
    `;
    card.addEventListener('click', () => addToCart(p));
    grid.appendChild(card);
  });

  updateFloatingCart();
}

function addToCart(product) {
  const existing = STATE.cart.find(i => i.product.id === product.id);
  if (existing) {
    if (!STATE.settings.allowNegativeStock && existing.qty >= product.stock) {
      alert(`⚠️ Stock insuffisant pour ${product.name} (Stock actuel: ${product.stock})`);
      return;
    }
    existing.qty++;
  } else {
    if (!STATE.settings.allowNegativeStock && product.stock <= 0) {
      alert(`⚠️ Article en rupture de stock (${product.name})`);
      return;
    }
    STATE.cart.push({ product, qty: 1 });
  }
  updateFloatingCart();
}

function updateFloatingCart() {
  const bar = document.getElementById('floatingCartBar');
  const countSpan = document.getElementById('floatCartCount');
  const totalSpan = document.getElementById('floatCartTotal');

  const totalCount = STATE.cart.reduce((sum, i) => sum + i.qty, 0);
  const totalAmount = getCartTotal();

  if (totalCount > 0) {
    bar.style.display = 'flex';
    countSpan.textContent = totalCount;
    totalSpan.textContent = totalAmount.toLocaleString('fr-FR');
  } else {
    bar.style.display = 'none';
  }
}

function getCartSubtotal() {
  return STATE.cart.reduce((sum, i) => sum + (i.product.salePrice * i.qty), 0);
}

function getCartTotal() {
  let sub = getCartSubtotal();
  if (STATE.appliedPromo) {
    sub = sub * (1 - STATE.appliedPromo.discountPercent / 100);
  }
  if (STATE.settings.taxEnabled) {
    sub = sub * (1 + STATE.settings.taxRatePercent / 100);
  }
  return Math.round(sub);
}

function renderCartModal() {
  const list = document.getElementById('cartItemsList');
  list.innerHTML = '';

  STATE.cart.forEach((item, idx) => {
    const div = document.createElement('div');
    div.className = 'cart-item';
    div.innerHTML = `
      <div style="flex: 1;">
        <div style="font-weight: 600; font-size: 14px;">${item.product.name}</div>
        <div style="font-size: 12px; color: var(--text-muted);">${item.product.salePrice.toLocaleString('fr-FR')} CFA × ${item.qty} = ${(item.product.salePrice * item.qty).toLocaleString('fr-FR')} CFA</div>
      </div>
      <div class="qty-control">
        <button class="qty-btn btn-minus" data-idx="${idx}">-</button>
        <span style="font-weight: 700; min-width: 20px; text-align: center;">${item.qty}</span>
        <button class="qty-btn btn-plus" data-idx="${idx}">+</button>
      </div>
    `;
    list.appendChild(div);
  });

  list.querySelectorAll('.btn-minus').forEach(btn => {
    btn.addEventListener('click', () => {
      const i = parseInt(btn.dataset.idx);
      if (STATE.cart[i].qty > 1) {
        STATE.cart[i].qty--;
      } else {
        requestAdminPin("Supprimer cet article du panier", () => {
          STATE.cart.splice(i, 1);
          renderCartModal();
          updateFloatingCart();
        });
        return;
      }
      renderCartModal();
      updateFloatingCart();
    });
  });

  list.querySelectorAll('.btn-plus').forEach(btn => {
    btn.addEventListener('click', () => {
      const i = parseInt(btn.dataset.idx);
      addToCart(STATE.cart[i].product);
      renderCartModal();
    });
  });

  document.getElementById('cartSubtotalText').textContent = getCartSubtotal().toLocaleString('fr-FR') + ' CFA';
  if (STATE.settings.taxEnabled) {
    document.getElementById('cartTaxRow').style.display = 'flex';
    const taxVal = Math.round(getCartSubtotal() * (STATE.settings.taxRatePercent / 100));
    document.getElementById('cartTaxText').textContent = taxVal.toLocaleString('fr-FR') + ' CFA';
  } else {
    document.getElementById('cartTaxRow').style.display = 'none';
  }
  document.getElementById('cartTotalText').textContent = getCartTotal().toLocaleString('fr-FR') + ' CFA';
}

// ==========================================
// 5. BLUETOOTH THERMAL PRINTER DRIVER (Web Bluetooth ESC/POS with Auto-Reconnect)
// ==========================================
function updateBleHeaderStatus(connected, deviceName = '') {
  const btn = document.getElementById('btnHeaderBle');
  if (btn) {
    if (connected) {
      btn.style.color = 'var(--primary)';
      btn.title = `Imprimante connectée : ${deviceName}`;
    } else {
      btn.style.color = '';
      btn.title = "Connecter une imprimante Bluetooth";
    }
  }
}

async function connectBluetoothPrinter() {
  if (!navigator.bluetooth) {
    alert("⚠️ Web Bluetooth n'est pas supporté directement par Safari classique. Sur iOS, utilisez l'application Bluefy ou connectez une imprimante Wi-Fi / AirPrint !");
    return false;
  }
  try {
    const device = await navigator.bluetooth.requestDevice({
      acceptAllDevices: true,
      optionalServices: [
        '000018f0-0000-1000-8000-00805f9b34fb',
        'e7810a71-73ae-499d-8c15-faa9aef0c3f2',
        '49535343-fe7d-4ae5-8fa9-9fafd205e455'
      ]
    });
    
    device.addEventListener('gattserverdisconnected', () => {
      console.log("Bluetooth printer disconnected (idle/background)");
      updateBleHeaderStatus(false);
    });

    const server = await device.gatt.connect();
    const services = await server.getPrimaryServices();
    for (const s of services) {
      const chars = await s.getCharacteristics();
      for (const c of chars) {
        if (c.properties.write || c.properties.writeWithoutResponse) {
          STATE.bleCharacteristic = c;
          STATE.bleDevice = device;
          updateBleHeaderStatus(true, device.name);
          alert(`✅ Imprimante Bluetooth connectée : ${device.name || 'Imprimante ESC/POS'}`);
          return true;
        }
      }
    }
    return false;
  } catch (err) {
    console.error("Bluetooth connection error:", err);
    return false;
  }
}

async function ensurePrinterConnected() {
  if (STATE.bleDevice && STATE.bleDevice.gatt.connected && STATE.bleCharacteristic) {
    return true;
  }
  if (STATE.bleDevice && !STATE.bleDevice.gatt.connected) {
    try {
      console.log("Tentative de reconnexion automatique à l'imprimante...");
      const server = await STATE.bleDevice.gatt.connect();
      const services = await server.getPrimaryServices();
      for (const s of services) {
        const chars = await s.getCharacteristics();
        for (const c of chars) {
          if (c.properties.write || c.properties.writeWithoutResponse) {
            STATE.bleCharacteristic = c;
            updateBleHeaderStatus(true, STATE.bleDevice.name);
            return true;
          }
        }
      }
    } catch (e) {
      console.warn("Reconnexion auto échouée, ouverture du sélecteur", e);
    }
  }
  return await connectBluetoothPrinter();
}

async function sendToPrinter(rawBytes) {
  const isConnected = await ensurePrinterConnected();
  if (isConnected && STATE.bleCharacteristic) {
    try {
      const chunkSize = 100;
      for (let i = 0; i < rawBytes.length; i += chunkSize) {
        const chunk = rawBytes.slice(i, i + chunkSize);
        await STATE.bleCharacteristic.writeValue(chunk);
      }
      return true;
    } catch (e) {
      console.error("Erreur d'envoi Bluetooth:", e);
      window.print();
      return false;
    }
  } else {
    // Fallback to iOS System Print
    window.print();
    return false;
  }
}

// ==========================================
// 6. RECEIPT GENERATOR & PRINTING
// ==========================================
function removeAccents(str) {
  if (!str) return '';
  return String(str)
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/œ/g, "oe")
    .replace(/æ/g, "ae")
    .replace(/[^\x20-\x7E\n]/g, ""); // Keep pure ASCII for thermal print heads
}

function padLine(left, right, width, fill = ' ') {
  left = String(left || '');
  right = String(right || '');
  const maxLeft = width - right.length - 1;
  if (left.length > maxLeft) {
    left = left.substring(0, maxLeft);
  }
  const spaces = Math.max(1, width - left.length - right.length);
  return left + fill.repeat(spaces) + right;
}

function centerText(text, width) {
  text = String(text || '').trim();
  if (text.length >= width) return text.substring(0, width);
  const leftPad = Math.floor((width - text.length) / 2);
  const rightPad = width - text.length - leftPad;
  return ' '.repeat(leftPad) + text + ' '.repeat(rightPad);
}

function generateReceiptPlainText(ticket) {
  const width = (STATE.settings.printerWidth === "80") ? 42 : 32;
  const sep = '='.repeat(width);
  const dash = '-'.repeat(width);
  const dateStr = new Date(ticket.date).toLocaleString('fr-FR');

  let out = [];
  out.push(sep);
  out.push(centerText(removeAccents(STATE.settings.shopName || "SYPOS COMMERCE").toUpperCase(), width));
  if (STATE.settings.shopAddress) {
    out.push(centerText(removeAccents(STATE.settings.shopAddress), width));
  }
  if (STATE.settings.shopPhone) {
    out.push(centerText("Tel: " + removeAccents(STATE.settings.shopPhone), width));
  }
  out.push(sep);
  out.push(padLine("Ticket:", ticket.number, width));
  out.push(padLine("Date:", dateStr, width));
  out.push(padLine("Vendeur:", removeAccents(STATE.settings.sellerName || "Caisse"), width));
  out.push(dash);

  ticket.items.forEach(it => {
    const name = removeAccents(it.productName);
    const totalLine = (it.price * it.qty).toLocaleString('fr-FR') + ' F';
    const qtyLine = `${it.qty}x${it.price.toLocaleString('fr-FR')}`;
    
    // Check if item fits in one line with dots
    const leftText = `${name} (${qtyLine})`;
    if (leftText.length + totalLine.length + 2 <= width) {
      out.push(padLine(leftText, totalLine, width, '.'));
    } else {
      // Print name on first line, and qty + price on second line
      out.push(name.substring(0, width));
      out.push(padLine(`  ${qtyLine}`, totalLine, width, '.'));
    }
  });

  out.push(dash);
  out.push(padLine("TOTAL NET :", ticket.totalAmount.toLocaleString('fr-FR') + ' CFA', width));
  out.push(padLine("Reglement :", removeAccents((ticket.paymentMethod || 'ESPECES').toUpperCase()), width));

  if (ticket.cashReceived) {
    out.push(padLine("Recu :", ticket.cashReceived.toLocaleString('fr-FR') + ' CFA', width));
  }
  if (ticket.changeGiven) {
    out.push(padLine("Rendu :", ticket.changeGiven.toLocaleString('fr-FR') + ' CFA', width));
  }
  out.push(sep);

  if (STATE.settings.receiptFooter) {
    out.push(centerText(removeAccents(STATE.settings.receiptFooter), width));
  }
  if (STATE.settings.showPublisherSignature) {
    out.push(centerText(removeAccents(STATE.settings.publisherSignatureText || "SYPOS MOBILE 0758245530"), width));
  }
  
  out.push('\n\n'); // Line feeds for paper cut
  return out.join('\n');
}

function generateReceiptHtml(ticket) {
  const dateStr = new Date(ticket.date).toLocaleString('fr-FR');
  let itemsHtml = '';
  ticket.items.forEach(it => {
    itemsHtml += `
      <div class="receipt-row">
        <span>${it.productName} ×${it.qty}</span>
        <span>${(it.price * it.qty).toLocaleString('fr-FR')} CFA</span>
      </div>
    `;
  });

  return `
    <div class="receipt-center receipt-bold" style="font-size: 14px;">${STATE.settings.shopName.toUpperCase()}</div>
    <div class="receipt-center">${STATE.settings.shopAddress}</div>
    <div class="receipt-center">Tél: ${STATE.settings.shopPhone}</div>
    <div class="receipt-divider"></div>
    <div class="receipt-row"><span>Ticket N°:</span><span>${ticket.number}</span></div>
    <div class="receipt-row"><span>Date:</span><span>${dateStr}</span></div>
    <div class="receipt-row"><span>Vendeur:</span><span>${STATE.settings.sellerName}</span></div>
    <div class="receipt-divider"></div>
    ${itemsHtml}
    <div class="receipt-divider"></div>
    <div class="receipt-row receipt-bold" style="font-size: 14px;">
      <span>TOTAL NET :</span>
      <span>${ticket.totalAmount.toLocaleString('fr-FR')} CFA</span>
    </div>
    <div class="receipt-row"><span>Règlement :</span><span>${ticket.paymentMethod.toUpperCase()}</span></div>
    ${ticket.cashReceived ? `<div class="receipt-row"><span>Reçu :</span><span>${ticket.cashReceived.toLocaleString('fr-FR')} CFA</span></div>` : ''}
    ${ticket.changeGiven ? `<div class="receipt-row"><span>Rendu :</span><span>${ticket.changeGiven.toLocaleString('fr-FR')} CFA</span></div>` : ''}
    <div class="receipt-divider"></div>
    <div class="receipt-center" style="font-size: 11px;">${STATE.settings.receiptFooter}</div>
    ${STATE.settings.showPublisherSignature ? `<div class="receipt-center" style="font-size: 9px; color:#666; margin-top:6px;">${STATE.settings.publisherSignatureText}</div>` : ''}
  `;
}

// ==========================================
// 6.2 Z-REPORT & CLÔTURE DE CAISSE
// ==========================================
function getZReportData() {
  const now = new Date();
  const dateText = now.toLocaleDateString('fr-FR') + ' ' + now.toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' });
  
  let totalSales = 0;
  let ticketsCount = STATE.tickets.length;
  let cashSales = 0;
  let waveSales = 0;
  let omSales = 0;
  let momoSales = 0;
  let cardSales = 0;
  let creditSales = 0;

  STATE.tickets.forEach(t => {
    totalSales += t.totalAmount;
    const m = (t.paymentMethod || 'cash').toLowerCase();
    if (m === 'cash' || m === 'especes') cashSales += t.totalAmount;
    else if (m === 'wave') waveSales += t.totalAmount;
    else if (m === 'om' || m === 'orange') omSales += t.totalAmount;
    else if (m === 'momo' || m === 'mtn') momoSales += t.totalAmount;
    else if (m === 'card' || m === 'carte') cardSales += t.totalAmount;
    else if (m === 'credit') creditSales += t.totalAmount;
  });

  const totalExpenses = (STATE.expenses || []).reduce((sum, e) => sum + (e.amount || 0), 0);
  const netCashInDrawer = Math.max(0, cashSales - totalExpenses);

  return {
    dateText,
    totalSales,
    ticketsCount,
    totalExpenses,
    cashSales,
    waveSales,
    omSales,
    momoSales,
    cardSales,
    creditSales,
    netCashInDrawer
  };
}

function generateZReportPlainText(zData) {
  const width = (STATE.settings.printerWidth === "80") ? 42 : 32;
  const sep = '='.repeat(width);
  const dash = '-'.repeat(width);

  let out = [];
  out.push(sep);
  out.push(centerText("RAPPORT Z DE CAISSE", width));
  out.push(centerText(removeAccents(STATE.settings.shopName || "SYPOS COMMERCE").toUpperCase(), width));
  out.push(centerText("Cloture: " + zData.dateText, width));
  if (STATE.settings.sellerName) {
    out.push(centerText("Responsable: " + removeAccents(STATE.settings.sellerName), width));
  }
  out.push(sep);
  out.push(padLine("TOTAL VENTES :", zData.totalSales.toLocaleString('fr-FR') + ' CFA', width));
  out.push(padLine("Nombre Tickets :", String(zData.ticketsCount), width));
  if (zData.totalExpenses > 0) {
    out.push(padLine("Total Depenses :", '-' + zData.totalExpenses.toLocaleString('fr-FR') + ' CFA', width));
  }
  out.push(dash);
  out.push("Ventilation Encaissements:");
  if (zData.cashSales > 0) out.push(padLine("  - Especes (Cash):", zData.cashSales.toLocaleString('fr-FR') + ' F', width));
  if (zData.waveSales > 0) out.push(padLine("  - Wave:", zData.waveSales.toLocaleString('fr-FR') + ' F', width));
  if (zData.omSales > 0) out.push(padLine("  - Orange Money:", zData.omSales.toLocaleString('fr-FR') + ' F', width));
  if (zData.momoSales > 0) out.push(padLine("  - MTN MoMo:", zData.momoSales.toLocaleString('fr-FR') + ' F', width));
  if (zData.cardSales > 0) out.push(padLine("  - Carte Bancaire:", zData.cardSales.toLocaleString('fr-FR') + ' F', width));
  if (zData.creditSales > 0) out.push(padLine("  - Ventes a Credit:", zData.creditSales.toLocaleString('fr-FR') + ' F', width));
  out.push(sep);
  out.push(padLine("CASH NET EN CAISSE :", zData.netCashInDrawer.toLocaleString('fr-FR') + ' CFA', width));
  out.push(sep);
  out.push(centerText("Signature Responsable:", width));
  out.push('\n\n');
  out.push(centerText("................................", width));
  out.push('\n\n\n');
  return out.join('\n');
}

function generateZReportHtml(zData) {
  return `
    <div class="receipt-center receipt-bold" style="font-size: 15px;">RAPPORT Z DE CAISSE</div>
    <div class="receipt-center receipt-bold">${STATE.settings.shopName.toUpperCase()}</div>
    <div class="receipt-center" style="font-size: 11px; color: #666;">Date de clôture : ${zData.dateText}</div>
    <div class="receipt-center" style="font-size: 11px;">Responsable : ${STATE.settings.sellerName}</div>
    <div class="receipt-divider"></div>
    <div class="receipt-row receipt-bold" style="font-size: 14px;">
      <span>TOTAL VENTES :</span>
      <span style="color: var(--primary);">${zData.totalSales.toLocaleString('fr-FR')} CFA</span>
    </div>
    <div class="receipt-row"><span>Nombre de Tickets :</span><span>${zData.ticketsCount}</span></div>
    ${zData.totalExpenses > 0 ? `<div class="receipt-row" style="color: var(--danger);"><span>Dépenses / Sorties :</span><span>-${zData.totalExpenses.toLocaleString('fr-FR')} CFA</span></div>` : ''}
    <div class="receipt-divider"></div>
    <div style="font-size: 12px; font-weight: 700; margin-bottom: 4px;">Ventilation des Règlements :</div>
    ${zData.cashSales > 0 ? `<div class="receipt-row"><span>💵 Espèces (Cash) :</span><span>${zData.cashSales.toLocaleString('fr-FR')} CFA</span></div>` : ''}
    ${zData.waveSales > 0 ? `<div class="receipt-row"><span>🌊 Wave :</span><span>${zData.waveSales.toLocaleString('fr-FR')} CFA</span></div>` : ''}
    ${zData.omSales > 0 ? `<div class="receipt-row"><span>🟠 Orange Money :</span><span>${zData.omSales.toLocaleString('fr-FR')} CFA</span></div>` : ''}
    ${zData.momoSales > 0 ? `<div class="receipt-row"><span>🟡 MTN MoMo :</span><span>${zData.momoSales.toLocaleString('fr-FR')} CFA</span></div>` : ''}
    ${zData.cardSales > 0 ? `<div class="receipt-row"><span>💳 Carte Bancaire :</span><span>${zData.cardSales.toLocaleString('fr-FR')} CFA</span></div>` : ''}
    ${zData.creditSales > 0 ? `<div class="receipt-row"><span>📝 Ventes à Crédit :</span><span>${zData.creditSales.toLocaleString('fr-FR')} CFA</span></div>` : ''}
    <div class="receipt-divider"></div>
    <div class="receipt-row receipt-bold" style="font-size: 15px; background: #e8f5e9; padding: 6px; border-radius: 8px;">
      <span>CASH NET EN CAISSE :</span>
      <span style="color: #2e7d32;">${zData.netCashInDrawer.toLocaleString('fr-FR')} CFA</span>
    </div>
    <div class="receipt-divider"></div>
    <div class="receipt-center" style="font-size: 10px; color: #888;">Signature Responsable :</div>
    <div style="height: 35px;"></div>
    <div class="receipt-center" style="color: #aaa;">................................</div>
  `;
}

function exportToCsv(filename, headers, rows) {
  let csvContent = "data:text/csv;charset=utf-8,\uFEFF";
  csvContent += headers.map(h => `"${h}"`).join(";") + "\r\n";
  rows.forEach(r => {
    csvContent += r.map(val => `"${String(val || '').replace(/"/g, '""')}"`).join(";") + "\r\n";
  });
  const encodedUri = encodeURI(csvContent);
  const link = document.createElement("a");
  link.setAttribute("href", encodedUri);
  link.setAttribute("download", filename);
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
}

function createEscPosBarcodeLabelBytes(shopName, prodName, price, barcode) {
  const init = [0x1B, 0x40, 0x1C, 0x2E, 0x1B, 0x74, 0x00];
  const center = [0x1B, 0x61, 0x01];
  const boldOn = [0x1B, 0x45, 0x01];
  const boldOff = [0x1B, 0x45, 0x00];
  const dHeightOn = [0x1B, 0x21, 0x10];
  const dHeightOff = [0x1B, 0x21, 0x00];

  function strToBytes(str) {
    const b = [];
    const ascii = removeAccents(str);
    for (let i = 0; i < ascii.length; i++) {
      let c = ascii.charCodeAt(i);
      b.push((c >= 32 && c <= 126) || c === 10 ? c : 32);
    }
    return b;
  }

  let textBytes = [
    ...center, ...boldOn,
    ...strToBytes((shopName || "SYPOS").toUpperCase() + "\n"),
    ...boldOff,
    ...strToBytes((prodName || "Article") + "\n"),
    ...boldOn, ...dHeightOn,
    ...strToBytes(price + " CFA\n"),
    ...dHeightOff, ...boldOff
  ];

  let barcodeBytes = [];
  const cleanCode = (barcode || '').replace(/[^0-9A-Za-z]/g, '');
  if (cleanCode.length > 0) {
    const codeAscii = [];
    for (let i = 0; i < cleanCode.length; i++) {
      codeAscii.push(cleanCode.charCodeAt(i));
    }
    barcodeBytes = [
      0x1D, 0x68, 60, // GS h 60 (Height 60 dots)
      0x1D, 0x77, 2,  // GS w 2 (Module width 2)
      0x1D, 0x48, 2,  // GS H 2 (HRI below barcode)
      0x1D, 0x6B, 73, codeAscii.length, ...codeAscii,
      0x0A
    ];
  }

  const footer = [0x0A, 0x0A, 0x1B, 0x64, 0x03];
  return new Uint8Array([...init, ...textBytes, ...barcodeBytes, ...footer]);
}

// ==========================================
// 7. CRYPTOGRAPHIC LICENSE MANAGEMENT (RSA-2048)
// ==========================================
const RSA_PUBLIC_KEY_B64 = 
  "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAy4fzpl4lsQkScqSwHrAx" +
  "CiUEndXd1Kop9u/kKdGtxmvvJzxD15KN71TJ8ZVw/ds749Pz3yCdzlU7io5fDshu" +
  "VLYNVCXIpOJUtjGdtxVNCv9l0+bpAUKLZpPEHGgyXlDGbVE8G6bOQXXt57CoFdTQ" +
  "iGm7iBuQ2jcI6tW2Y0BZztforF57YX133ls9Eex5aM7pjzQcY31hrlLDKN3+1CD+" +
  "/XFSfPw5WfcLlOYm0x6FzFkFhG6s5qSNB1cfQ5yjScXvHGXoRa5Eo/MH4BXkU2vT" +
  "IZS5Y4+gqdc4T6drGyzQ2uUUtsII8zNSh0gRZAja452p4EuwpyrcgcopZG497gHp" +
  "5QIDAQAB";

function getDeviceId() {
  let devId = localStorage.getItem('sypos_device_id');
  if (!devId) {
    const rawFp = (navigator.userAgent || '') + '|' + screen.width + 'x' + screen.height + '|' + (navigator.hardwareConcurrency || 4) + '|' + Date.now();
    let hash = 0;
    for (let i = 0; i < rawFp.length; i++) {
      hash = ((hash << 5) - hash) + rawFp.charCodeAt(i);
      hash |= 0;
    }
    const hex1 = Math.abs(hash).toString(16).toUpperCase().padStart(4, '0').substring(0, 4);
    const hex2 = Math.floor(1000 + Math.random() * 9000).toString(16).toUpperCase().padStart(4, '0').substring(0, 4);
    devId = `SYPOS-DEV-${hex1}-${hex2}`;
    localStorage.setItem('sypos_device_id', devId);
  }
  return devId;
}

function base64UrlDecode(str) {
  let b64 = str.replace(/-/g, '+').replace(/_/g, '/');
  while (b64.length % 4) b64 += '=';
  return (typeof forge !== 'undefined') ? forge.util.decode64(b64) : atob(b64);
}

function validateLicenseToken(rawKey) {
  const token = (rawKey || '').trim().replace(/[`\s\r\n]/g, '');
  if (!token) return { isValid: false, message: "Veuillez saisir votre clé de licence." };
  if (!token.startsWith('SYP1.')) return { isValid: false, message: "Clé invalide (doit commencer par 'SYP1.')." };

  const parts = token.split('.');
  if (parts.length !== 3) return { isValid: false, message: "Structure de clé corrompue." };

  try {
    const payloadBytes = base64UrlDecode(parts[1]);
    const sigBytes = base64UrlDecode(parts[2]);

    if (typeof forge === 'undefined') {
      return { isValid: false, message: "Module cryptographique non chargé. Vérifiez votre connexion." };
    }

    const der = forge.util.decode64(RSA_PUBLIC_KEY_B64);
    const asn1 = forge.asn1.fromDer(der);
    const pubKey = forge.pki.publicKeyFromAsn1(asn1);

    const md = forge.md.sha256.create();
    md.update(payloadBytes, 'raw');
    const isSignatureValid = pubKey.verify(md.digest().bytes(), sigBytes);

    if (!isSignatureValid) {
      return { isValid: false, message: "Signature invalide : cette clé est falsifiée ou corrompue." };
    }

    const payload = JSON.parse(payloadBytes);
    const targetDevId = (payload.devId || '').trim().toUpperCase();
    const currentDevId = getDeviceId().trim().toUpperCase();

    if (targetDevId !== currentDevId) {
      return { isValid: false, message: `Clé prévue pour l'appareil (${targetDevId}), incompatible avec (${currentDevId}).` };
    }

    const now = Date.now();
    if (payload.exp && payload.exp > 0 && now > payload.exp) {
      const expDate = new Date(payload.exp).toLocaleDateString('fr-FR');
      return { isValid: false, message: `Cette licence a expiré le ${expDate}.` };
    }

    const planLabel = payload.plan === 'LIFETIME' ? 'Définitive (À Vie)' : (payload.plan === 'ANNUAL' ? 'Annuelle Pro' : 'Commerciale');

    return {
      isValid: true,
      licenseType: planLabel,
      expiryDate: payload.exp || 0,
      shopName: payload.shop || STATE.settings.shopName,
      message: `✅ Licence ${planLabel} activée avec succès !`
    };
  } catch (err) {
    return { isValid: false, message: "Erreur de validation : " + err.message };
  }
}

function isLicenseActive() {
  if (!STATE.settings.isLicensed) return false;
  if (STATE.settings.licenseExpiryDate > 0 && Date.now() > STATE.settings.licenseExpiryDate) {
    return false;
  }
  return true;
}

// ==========================================
// 8. ADMIN PIN SECURITY CHECK
// ==========================================
function requestAdminPin(actionDesc, onSuccess) {
  STATE.pendingPinAction = onSuccess;
  document.getElementById('pinActionDescription').textContent = actionDesc;
  document.getElementById('pinInputCode').value = '';
  document.getElementById('modalPin').classList.add('active');
}

// ==========================================
// 9. INITIALIZATION & EVENT LISTENERS
// ==========================================
document.addEventListener('DOMContentLoaded', () => {
  loadFromStorage();
  initTabs();
  renderPos();

  // License System Setup & Verification
  const currentDevId = getDeviceId();
  const licenseDevIdEl = document.getElementById('licenseDialogDevId');
  if (licenseDevIdEl) licenseDevIdEl.textContent = currentDevId;

  function showLicenseModal(isInitial = false) {
    const modal = document.getElementById('modalLicense');
    const closeBtn = document.getElementById('btnCloseLicenseModal');
    if (closeBtn) closeBtn.style.display = isInitial ? 'none' : 'block';
    const feedback = document.getElementById('licenseFeedbackMsg');
    if (feedback) feedback.style.display = 'none';
    modal.classList.add('active');
  }

  // Check License on Startup (Auto lock if unactivated)
  if (!isLicenseActive()) {
    showLicenseModal(true);
  }

  // Copy Device ID button
  document.getElementById('btnCopyDeviceId').addEventListener('click', () => {
    if (navigator.clipboard) {
      navigator.clipboard.writeText(currentDevId).then(() => {
        alert("📋 ID Appareil copié dans le presse-papier :\n" + currentDevId);
      });
    } else {
      alert("Votre ID Appareil : " + currentDevId);
    }
  });

  // WhatsApp Order License button
  document.getElementById('btnWhatsAppLicense').addEventListener('click', () => {
    const phone = STATE.settings.shopPhone ? STATE.settings.shopPhone.replace(/[^0-9]/g, '') : "2250758245530";
    const msg = `Bonjour SYPOS, je souhaite activer la licence commerciale pour mon appareil :\n🆔 ID Appareil : *${currentDevId}*`;
    window.open(`https://api.whatsapp.com/send?phone=${phone}&text=${encodeURIComponent(msg)}`, '_blank');
  });

  // Validate License Button
  document.getElementById('btnValidateLicense').addEventListener('click', () => {
    const key = document.getElementById('inputLicenseKey').value;
    const res = validateLicenseToken(key);
    const feedback = document.getElementById('licenseFeedbackMsg');
    feedback.style.display = 'block';
    if (res.isValid) {
      feedback.style.background = '#e8f5e9';
      feedback.style.color = '#2e7d32';
      feedback.textContent = res.message;
      STATE.settings.isLicensed = true;
      STATE.settings.licenseKey = key.trim();
      STATE.settings.licenseType = res.licenseType;
      STATE.settings.licenseExpiryDate = res.expiryDate;
      saveToStorage();
      setTimeout(() => {
        document.getElementById('modalLicense').classList.remove('active');
        renderSettings();
      }, 1500);
    } else {
      feedback.style.background = '#ffebee';
      feedback.style.color = '#c62828';
      feedback.textContent = res.message;
    }
  });

  // Scan License QR Code button
  document.getElementById('btnScanLicenseQr').addEventListener('click', () => {
    document.getElementById('modalLicense').classList.remove('active');
    document.getElementById('modalScanner').classList.add('active');
    const qr = new Html5Qrcode("qr-reader");
    STATE.html5QrCode = qr;
    qr.start({ facingMode: "environment" }, { fps: 10, qrbox: { width: 250, height: 250 } }, (token) => {
      qr.stop().then(() => {
        document.getElementById('modalScanner').classList.remove('active');
        document.getElementById('inputLicenseKey').value = token;
        document.getElementById('modalLicense').classList.add('active');
        document.getElementById('btnValidateLicense').click();
      });
    }).catch(err => {
      alert("Impossible d'ouvrir la caméra : " + err);
      document.getElementById('modalScanner').classList.remove('active');
      document.getElementById('modalLicense').classList.add('active');
    });
  });

  // Demo Mode button
  const btnDemo = document.getElementById('btnDemoMode');
  if (btnDemo) {
    btnDemo.addEventListener('click', () => {
      document.getElementById('modalLicense').classList.remove('active');
    });
  }

  // Open License Modal from Settings
  const btnOpenLicense = document.getElementById('btnOpenLicenseModal');
  if (btnOpenLicense) {
    btnOpenLicense.addEventListener('click', () => showLicenseModal(false));
  }

  // Search input live filter
  document.getElementById('posSearchInput').addEventListener('input', renderPos);

  // Floating cart click
  document.getElementById('floatingCartBar').addEventListener('click', () => {
    renderCartModal();
    document.getElementById('modalCart').classList.add('active');
  });

  // Modal Close buttons
  document.querySelectorAll('.close-modal').forEach(btn => {
    btn.addEventListener('click', () => {
      btn.closest('.modal-overlay').classList.remove('active');
    });
  });

  // Clear Cart
  document.getElementById('btnClearCartBtn').addEventListener('click', () => {
    requestAdminPin("Vider complètement le panier", () => {
      STATE.cart = [];
      STATE.appliedPromo = null;
      document.getElementById('modalCart').classList.remove('active');
      updateFloatingCart();
      renderPos();
    });
  });

  // Checkout button
  document.getElementById('btnGoToCheckout').addEventListener('click', () => {
    document.getElementById('modalCart').classList.remove('active');
    const total = getCartTotal();
    document.getElementById('payAmountDue').textContent = total.toLocaleString('fr-FR') + ' CFA';
    document.getElementById('payCashReceivedInput').value = '';
    document.getElementById('payChangeDueBox').style.display = 'none';

    // Populate customers
    const custSelect = document.getElementById('payCustomerSelect');
    custSelect.innerHTML = '<option value="">Client Comptant</option>';
    STATE.customers.forEach(c => {
      custSelect.innerHTML += `<option value="${c.id}">${c.name}</option>`;
    });

    document.getElementById('modalPayment').classList.add('active');
  });

  // Payment Method change
  document.getElementById('payPaymentMethod').addEventListener('change', (e) => {
    const isCash = e.target.value === 'cash';
    const isCredit = e.target.value === 'credit';
    document.getElementById('payCashReceivedGroup').style.display = isCash ? 'block' : 'none';
    document.getElementById('payCustomerGroup').style.display = isCredit ? 'block' : 'none';
  });

  // Cash Received live calculation
  document.getElementById('payCashReceivedInput').addEventListener('input', (e) => {
    const rec = parseFloat(e.target.value) || 0;
    const total = getCartTotal();
    const box = document.getElementById('payChangeDueBox');
    if (rec >= total && total > 0) {
      box.style.display = 'block';
      document.getElementById('payChangeDueAmount').textContent = (rec - total).toLocaleString('fr-FR') + ' CFA';
    } else {
      box.style.display = 'none';
    }
  });

  // Confirm Payment & Record Sale
  document.getElementById('btnConfirmPayment').addEventListener('click', () => {
    const method = document.getElementById('payPaymentMethod').value;
    const total = getCartTotal();
    const rec = parseFloat(document.getElementById('payCashReceivedInput').value) || total;
    const change = Math.max(0, rec - total);

    const ticketNumber = "TK-" + String(STATE.tickets.length + 1).padStart(5, '0');
    const ticket = {
      id: "t_" + Date.now(),
      number: ticketNumber,
      date: new Date().toISOString(),
      items: STATE.cart.map(i => ({
        productId: i.product.id,
        productName: i.product.name,
        qty: i.qty,
        price: i.product.salePrice,
        costPrice: i.product.costPrice
      })),
      totalAmount: total,
      paymentMethod: method,
      cashReceived: method === 'cash' ? rec : null,
      changeGiven: method === 'cash' ? change : null,
      status: 'completed'
    };

    // Deduct stocks
    STATE.cart.forEach(i => {
      const prod = STATE.products.find(p => p.id === i.product.id);
      if (prod) prod.stock -= i.qty;
    });

    STATE.tickets.unshift(ticket);
    STATE.cart = [];
    STATE.appliedPromo = null;
    saveToStorage();

    document.getElementById('modalPayment').classList.remove('active');
    updateFloatingCart();
    renderPos();

    // Show Receipt
    document.getElementById('receiptPaperContent').innerHTML = generateReceiptHtml(ticket);
    document.getElementById('modalReceipt').classList.add('active');
  });

function createEscPosTicketBytes(plainText) {
  // ESC/POS Commands:
  // 0x1B 0x40 = ESC @ (Initialize printer hardware)
  // 0x1C 0x2E = FS . (Cancel Chinese/Kanji double-byte mode)
  // 0x1B 0x74 0x00 = ESC t 0 (Select Code Page 0: CP437 Standard Western ASCII)
  const header = [0x1B, 0x40, 0x1C, 0x2E, 0x1B, 0x74, 0x00];

  const body = [];
  for (let i = 0; i < plainText.length; i++) {
    const code = plainText.charCodeAt(i);
    // Printable standard ASCII (32 to 126), newline (10), carriage return (13)
    if (code === 10 || code === 13 || (code >= 32 && code <= 126)) {
      body.push(code);
    } else {
      body.push(32); // Space for unknown multi-byte characters
    }
  }

  // Paper Feed & Tear Space: 3 line feeds + ESC d 3
  const footer = [0x0A, 0x0A, 0x0A, 0x1B, 0x64, 0x03];
  return new Uint8Array([...header, ...body, ...footer]);
}

  // Direct Bluetooth ESC/POS Print button (Works with Web Bluetooth / Bluefy Browser / Chrome)
  const btnBleDirect = document.getElementById('btnPrintBleDirectBtn');
  if (btnBleDirect) {
    btnBleDirect.addEventListener('click', async () => {
      if (STATE.tickets.length === 0) return;
      const t = STATE.tickets[0];
      const plainText = generateReceiptPlainText(t);
      const rawBytes = createEscPosTicketBytes(plainText);
      const ok = await sendToPrinter(rawBytes);
      if (ok) {
        alert("✅ Ticket imprimé avec succès !");
      }
    });
  }

  // Print Receipt Button (Standard / AirPrint / PDF)
  document.getElementById('btnPrintReceiptBtn').addEventListener('click', () => {
    window.print();
  });

  // WhatsApp Share Receipt
  document.getElementById('btnShareWhatsAppBtn').addEventListener('click', () => {
    if (STATE.tickets.length === 0) return;
    const t = STATE.tickets[0];
    let text = `*${STATE.settings.shopName}*\nTicket N°: ${t.number}\nTotal: ${t.totalAmount} CFA\nMerci de votre confiance !`;
    window.open(`https://api.whatsapp.com/send?text=${encodeURIComponent(text)}`, '_blank');
  });

  // Admin PIN submission
  document.getElementById('btnSubmitPin').addEventListener('click', () => {
    const entered = document.getElementById('pinInputCode').value;
    if (entered === STATE.settings.adminPin) {
      document.getElementById('modalPin').classList.remove('active');
      if (STATE.pendingPinAction) {
        STATE.pendingPinAction();
        STATE.pendingPinAction = null;
      }
    } else {
      alert("❌ Code PIN incorrect !");
    }
  });

  // Bluetooth button in header (Android / Chrome Web Bluetooth)
  document.getElementById('btnHeaderBle').addEventListener('click', connectBluetoothPrinter);

  // Settings Save
  document.getElementById('btnSaveSettings').addEventListener('click', () => {
    STATE.settings.shopName = document.getElementById('setShopName').value;
    STATE.settings.shopAddress = document.getElementById('setShopAddress').value;
    STATE.settings.shopPhone = document.getElementById('setShopPhone').value;
    STATE.settings.sellerName = document.getElementById('setSellerName').value;
    STATE.settings.receiptFooter = document.getElementById('setReceiptFooter').value;
    STATE.settings.showPublisherSignature = document.getElementById('setShowSignature').checked;
    STATE.settings.taxEnabled = document.getElementById('setTaxEnabled').checked;
    STATE.settings.adminPin = document.getElementById('setAdminPin').value || "1234";
    STATE.settings.printerWidth = document.getElementById('setPrinterWidth').value || "58";
    saveToStorage();
    alert("✅ Paramètres enregistrés avec succès !");
  });

  // ==========================================
  // HELD SALES (PAUSE & RESUME)
  // ==========================================
  function updateHeldBadge() {
    const badge = document.getElementById('heldBadge');
    if (badge) {
      if (STATE.heldSales && STATE.heldSales.length > 0) {
        badge.style.display = 'block';
        badge.textContent = STATE.heldSales.length;
      } else {
        badge.style.display = 'none';
      }
    }
  }
  updateHeldBadge();

  function renderHeldSales() {
    const container = document.getElementById('heldSalesListContainer');
    container.innerHTML = '';
    if (!STATE.heldSales || STATE.heldSales.length === 0) {
      container.innerHTML = '<div style="text-align:center; color:var(--text-muted); padding:20px;">Aucune vente en attente</div>';
      return;
    }
    STATE.heldSales.forEach((h, idx) => {
      const card = document.createElement('div');
      card.className = 'card-group';
      card.style.marginBottom = '10px';
      card.innerHTML = `
        <div style="display:flex; justify-content:space-between; align-items:center;">
          <div>
            <div style="font-weight:700; font-size:14px;">Vente #${idx + 1} (${h.items.length} articles)</div>
            <div style="font-size:11px; color:var(--text-muted);">${new Date(h.date).toLocaleTimeString('fr-FR')}</div>
          </div>
          <div style="font-size:16px; font-weight:800; color:var(--primary);">${h.total.toLocaleString('fr-FR')} CFA</div>
        </div>
        <div style="display:flex; gap:8px; margin-top:10px;">
          <button class="btn-main resume-held-btn" data-id="${h.id}" style="margin:0; height:36px; font-size:13px;">▶️ Reprendre</button>
          <button class="btn-main btn-secondary del-held-btn" data-id="${h.id}" style="margin:0; width:auto; height:36px; color:var(--danger);">🗑️</button>
        </div>
      `;
      card.querySelector('.resume-held-btn').addEventListener('click', () => {
        if (STATE.cart.length > 0) {
          if (!confirm("Le panier actuel sera remplacé par cette vente en attente. Continuer ?")) return;
        }
        STATE.cart = h.items;
        STATE.heldSales = STATE.heldSales.filter(x => x.id !== h.id);
        saveToStorage();
        updateHeldBadge();
        updateFloatingCart();
        renderPos();
        document.getElementById('modalHeldSales').classList.remove('active');
      });
      card.querySelector('.del-held-btn').addEventListener('click', () => {
        STATE.heldSales = STATE.heldSales.filter(x => x.id !== h.id);
        saveToStorage();
        updateHeldBadge();
        renderHeldSales();
      });
      container.appendChild(card);
    });
  }

  const btnHeldHeader = document.getElementById('btnHeldSales');
  if (btnHeldHeader) {
    btnHeldHeader.addEventListener('click', () => {
      renderHeldSales();
      document.getElementById('modalHeldSales').classList.add('active');
    });
  }

  const btnHoldCart = document.getElementById('btnHoldCurrentCartBtn');
  if (btnHoldCart) {
    btnHoldCart.addEventListener('click', () => {
      if (!STATE.cart || STATE.cart.length === 0) {
        alert("⚠️ Le panier est vide !");
        return;
      }
      const heldItem = {
        id: "held_" + Date.now(),
        date: new Date().toISOString(),
        items: [...STATE.cart],
        total: getCartTotal()
      };
      if (!STATE.heldSales) STATE.heldSales = [];
      STATE.heldSales.push(heldItem);
      STATE.cart = [];
      STATE.appliedPromo = null;
      saveToStorage();
      updateHeldBadge();
      updateFloatingCart();
      renderPos();
      document.getElementById('modalHeldSales').classList.remove('active');
      alert("⏸️ Vente mise en attente avec succès !");
    });
  }

  // ==========================================
  // RAPPORT Z & CLÔTURE DE CAISSE
  // ==========================================
  const btnTriggerZ = document.getElementById('btnTriggerZReport');
  if (btnTriggerZ) {
    btnTriggerZ.addEventListener('click', () => {
      const zData = getZReportData();
      document.getElementById('zReportPaperContent').innerHTML = generateZReportHtml(zData);
      document.getElementById('modalZReport').classList.add('active');
    });
  }

  const btnPrintZBle = document.getElementById('btnPrintZReportBleBtn');
  if (btnPrintZBle) {
    btnPrintZBle.addEventListener('click', async () => {
      const zData = getZReportData();
      const plainText = generateZReportPlainText(zData);
      const rawBytes = createEscPosTicketBytes(plainText);
      const ok = await sendToPrinter(rawBytes);
      if (ok) alert("✅ Rapport Z imprimé avec succès !");
    });
  }

  const btnExportZPdf = document.getElementById('btnExportZReportPdf');
  if (btnExportZPdf) {
    btnExportZPdf.addEventListener('click', () => {
      const zData = getZReportData();
      const win = window.open('', '_blank');
      win.document.write(`
        <html><head><title>Rapport Z - ${zData.dateText}</title>
        <style>body { font-family: monospace; padding: 20px; max-width: 400px; margin: auto; }</style>
        </head><body>
        ${generateZReportHtml(zData)}
        <script>window.print();</script>
        </body></html>
      `);
      win.document.close();
    });
  }

  const btnExportZCsv = document.getElementById('btnExportZReportCsv');
  if (btnExportZCsv) {
    btnExportZCsv.addEventListener('click', () => {
      const z = getZReportData();
      const headers = ["Indicateur", "Valeur"];
      const rows = [
        ["Date Cloture", z.dateText],
        ["Boutique", STATE.settings.shopName],
        ["Responsable", STATE.settings.sellerName],
        ["Total Ventes", z.totalSales + " CFA"],
        ["Nombre de Tickets", z.ticketsCount],
        ["Total Depenses", z.totalExpenses + " CFA"],
        ["Especes (Cash)", z.cashSales + " CFA"],
        ["Wave", z.waveSales + " CFA"],
        ["Orange Money", z.omSales + " CFA"],
        ["MTN MoMo", z.momoSales + " CFA"],
        ["Carte Bancaire", z.cardSales + " CFA"],
        ["Ventes a Credit", z.creditSales + " CFA"],
        ["Net Cash en Caisse", z.netCashInDrawer + " CFA"]
      ];
      exportToCsv(`Rapport_Z_${Date.now()}.csv`, headers, rows);
    });
  }

  // ==========================================
  // PRODUCT CRUD (CATALOGUE)
  // ==========================================
  function openProductModal(prod = null) {
    const isEdit = !!prod;
    document.getElementById('modalProductTitle').textContent = isEdit ? "📦 Modifier l'Article" : "📦 Ajouter un Article";
    document.getElementById('prodEditId').value = isEdit ? prod.id : "";
    document.getElementById('prodInputName').value = isEdit ? prod.name : "";
    document.getElementById('prodInputCostPrice').value = isEdit ? (prod.costPrice || "") : "";
    document.getElementById('prodInputSalePrice').value = isEdit ? prod.salePrice : "";
    document.getElementById('prodInputStock').value = isEdit ? prod.stock : "";
    document.getElementById('prodInputBarcode').value = isEdit ? (prod.barcode || "") : "";

    // Update categories select
    const catSelect = document.getElementById('prodInputCategory');
    catSelect.innerHTML = '';
    const cats = STATE.categories && STATE.categories.length > 0 ? STATE.categories : [
      { id: "1", name: "Alimentation" },
      { id: "2", name: "Boissons" },
      { id: "3", name: "Hygiène" },
      { id: "4", name: "Divers" }
    ];
    cats.forEach(c => {
      const opt = document.createElement('option');
      opt.value = c.id;
      opt.textContent = c.name;
      if (isEdit && prod.catId === c.id) opt.selected = true;
      catSelect.appendChild(opt);
    });

    const delBtn = document.getElementById('btnDeleteProductModal');
    if (delBtn) delBtn.style.display = isEdit ? 'block' : 'none';

    document.getElementById('modalProduct').classList.add('active');
  }

  const btnAddProd = document.getElementById('btnAddNewProduct');
  if (btnAddProd) {
    btnAddProd.addEventListener('click', () => openProductModal(null));
  }

  const btnSaveProd = document.getElementById('btnSaveProductSubmit');
  if (btnSaveProd) {
    btnSaveProd.addEventListener('click', () => {
      const id = document.getElementById('prodEditId').value;
      const name = document.getElementById('prodInputName').value.trim();
      const catId = document.getElementById('prodInputCategory').value;
      const costPrice = parseFloat(document.getElementById('prodInputCostPrice').value) || 0;
      const salePrice = parseFloat(document.getElementById('prodInputSalePrice').value) || 0;
      const stock = parseInt(document.getElementById('prodInputStock').value, 10) || 0;
      const barcode = document.getElementById('prodInputBarcode').value.trim();

      if (!name) {
        alert("⚠️ Veuillez saisir la désignation de l'article !");
        return;
      }
      if (salePrice <= 0) {
        alert("⚠️ Veuillez saisir un prix de vente valide !");
        return;
      }

      if (id) {
        // Edit existing
        const p = STATE.products.find(x => x.id === id);
        if (p) {
          p.name = name;
          p.catId = catId;
          p.costPrice = costPrice;
          p.salePrice = salePrice;
          p.stock = stock;
          p.barcode = barcode;
        }
      } else {
        // Create new
        const newP = {
          id: "p_" + Date.now(),
          name,
          catId,
          costPrice,
          salePrice,
          stock,
          barcode
        };
        STATE.products.unshift(newP);
      }

      saveToStorage();
      renderProducts();
      renderPos();
      renderTools();
      document.getElementById('modalProduct').classList.remove('active');
      alert("✅ Article enregistré avec succès !");
    });
  }

  const btnDelProdModal = document.getElementById('btnDeleteProductModal');
  if (btnDelProdModal) {
    btnDelProdModal.addEventListener('click', () => {
      const id = document.getElementById('prodEditId').value;
      if (!id) return;
      requestAdminPin("Supprimer définitivement cet article", () => {
        STATE.products = STATE.products.filter(x => x.id !== id);
        saveToStorage();
        renderProducts();
        renderPos();
        renderTools();
        document.getElementById('modalProduct').classList.remove('active');
        alert("🗑️ Article supprimé !");
      });
    });
  }

  const btnAddCat = document.getElementById('btnAddCategoryPrompt');
  if (btnAddCat) {
    btnAddCat.addEventListener('click', () => {
      const name = prompt("Nom de la nouvelle catégorie :");
      if (name && name.trim()) {
        const newCat = { id: "cat_" + Date.now(), name: name.trim(), color: "#0D9488" };
        if (!STATE.categories) STATE.categories = [];
        STATE.categories.push(newCat);
        saveToStorage();
        openProductModal(STATE.products.find(x => x.id === document.getElementById('prodEditId').value));
      }
    });
  }

  const btnScanProdBarcode = document.getElementById('btnScanBarcodeForProduct');
  if (btnScanProdBarcode) {
    btnScanProdBarcode.addEventListener('click', () => {
      document.getElementById('modalScanner').classList.add('active');
      const qr = new Html5Qrcode("qr-reader");
      STATE.html5QrCode = qr;
      qr.start({ facingMode: "environment" }, { fps: 10, qrbox: { width: 250, height: 250 } }, (decodedText) => {
        qr.stop().then(() => {
          document.getElementById('modalScanner').classList.remove('active');
          document.getElementById('prodInputBarcode').value = decodedText;
        });
      }).catch(err => {
        alert("Impossible d'ouvrir la caméra : " + err);
        document.getElementById('modalScanner').classList.remove('active');
      });
    });
  }

  // ==========================================
  // EXPENSES (SORTIES DE CAISSE)
  // ==========================================
  const btnAddExp = document.getElementById('btnAddExpense');
  if (btnAddExp) {
    btnAddExp.addEventListener('click', () => {
      document.getElementById('expenseInputReason').value = '';
      document.getElementById('expenseInputAmount').value = '';
      document.getElementById('modalExpense').classList.add('active');
    });
  }

  const btnSaveExp = document.getElementById('btnSaveExpenseSubmit');
  if (btnSaveExp) {
    btnSaveExp.addEventListener('click', () => {
      const reason = document.getElementById('expenseInputReason').value.trim();
      const amount = parseFloat(document.getElementById('expenseInputAmount').value) || 0;
      if (!reason || amount <= 0) {
        alert("⚠️ Veuillez saisir le motif et un montant valide !");
        return;
      }
      if (!STATE.expenses) STATE.expenses = [];
      STATE.expenses.unshift({
        id: "exp_" + Date.now(),
        reason,
        amount,
        date: new Date().toISOString()
      });
      saveToStorage();
      renderReports();
      document.getElementById('modalExpense').classList.remove('active');
      alert("💸 Dépense enregistrée avec succès !");
    });
  }

  // ==========================================
  // CUSTOMER CREATION
  // ==========================================
  const btnAddCust = document.getElementById('btnAddCustomer');
  if (btnAddCust) {
    btnAddCust.addEventListener('click', () => {
      document.getElementById('custInputName').value = '';
      document.getElementById('custInputPhone').value = '';
      document.getElementById('custInputDebt').value = '';
      document.getElementById('modalCustomer').classList.add('active');
    });
  }

  const btnSaveCust = document.getElementById('btnSaveCustomerSubmit');
  if (btnSaveCust) {
    btnSaveCust.addEventListener('click', () => {
      const name = document.getElementById('custInputName').value.trim();
      const phone = document.getElementById('custInputPhone').value.trim();
      const debt = parseFloat(document.getElementById('custInputDebt').value) || 0;
      if (!name) {
        alert("⚠️ Veuillez saisir le nom du client !");
        return;
      }
      if (!STATE.customers) STATE.customers = [];
      STATE.customers.unshift({
        id: "c_" + Date.now(),
        name,
        phone,
        debt
      });
      saveToStorage();
      renderCustomers();
      document.getElementById('modalCustomer').classList.remove('active');
      alert("👤 Client enregistré avec succès !");
    });
  }

  // ==========================================
  // CSV & PDF EXPORTS FOR HISTORY & BILAN
  // ==========================================
  const btnExpHistCsv = document.getElementById('btnExportHistoryCsv');
  if (btnExpHistCsv) {
    btnExpHistCsv.addEventListener('click', () => {
      const headers = ["Ticket", "Date", "Montant Total", "Mode Reglement", "Articles"];
      const rows = STATE.tickets.map(t => [
        t.number,
        new Date(t.date).toLocaleString('fr-FR'),
        t.totalAmount + " CFA",
        (t.paymentMethod || 'ESPECES').toUpperCase(),
        t.items.map(i => `${i.productName} (x${i.qty})`).join(', ')
      ]);
      exportToCsv(`Historique_Ventes_${Date.now()}.csv`, headers, rows);
    });
  }

  const btnExpHistPdf = document.getElementById('btnExportHistoryPdf');
  if (btnExpHistPdf) {
    btnExpHistPdf.addEventListener('click', () => {
      const win = window.open('', '_blank');
      let rowsHtml = '';
      STATE.tickets.forEach(t => {
        rowsHtml += `<tr><td>${t.number}</td><td>${new Date(t.date).toLocaleString('fr-FR')}</td><td>${t.items.length}</td><td>${t.totalAmount} CFA</td><td>${t.paymentMethod}</td></tr>`;
      });
      win.document.write(`
        <html><head><title>Historique des Ventes</title>
        <style>body { font-family: sans-serif; padding: 20px; } table { width: 100%; border-collapse: collapse; } th, td { border: 1px solid #ccc; padding: 8px; text-align: left; } th { background: #f0f0f0; }</style>
        </head><body>
        <h2>Historique des Ventes — ${STATE.settings.shopName}</h2>
        <table><thead><tr><th>Ticket</th><th>Date</th><th>Articles</th><th>Montant</th><th>Règlement</th></tr></thead><tbody>${rowsHtml}</tbody></table>
        <script>window.print();</script></body></html>
      `);
      win.document.close();
    });
  }

  const btnExpBilanCsv = document.getElementById('btnExportBilanCsv');
  if (btnExpBilanCsv) {
    btnExpBilanCsv.addEventListener('click', () => {
      const z = getZReportData();
      const stockVal = STATE.products.reduce((sum, p) => sum + (p.stock * p.salePrice), 0);
      const headers = ["Poste", "Montant"];
      const rows = [
        ["Chiffre d'Affaires", z.totalSales + " CFA"],
        ["Nombre de Ventes", z.ticketsCount],
        ["Total Depenses", z.totalExpenses + " CFA"],
        ["Valeur du Stock", stockVal + " CFA"],
        ["Benefice Net Estime", (z.totalSales - z.totalExpenses) + " CFA"]
      ];
      exportToCsv(`Bilan_Financier_${Date.now()}.csv`, headers, rows);
    });
  }

  const btnExpBilanPdf = document.getElementById('btnExportBilanPdf');
  if (btnExpBilanPdf) {
    btnExpBilanPdf.addEventListener('click', () => {
      const z = getZReportData();
      const win = window.open('', '_blank');
      win.document.write(`
        <html><head><title>Bilan Financier</title>
        <style>body { font-family: sans-serif; padding: 20px; max-width: 500px; margin: auto; }</style>
        </head><body>
        ${generateZReportHtml(z)}
        <script>window.print();</script></body></html>
      `);
      win.document.close();
    });
  }

  // ==========================================
  // PRINT THERMAL BARCODE LABEL
  // ==========================================
  const btnPrintLabel = document.getElementById('btnPrintLabelNow');
  if (btnPrintLabel) {
    btnPrintLabel.addEventListener('click', async () => {
      const name = document.getElementById('toolNameInput').value || 'Article';
      const price = document.getElementById('toolPriceInput').value || '0';
      const barcode = document.getElementById('toolBarcodeInput').value || '';
      
      const rawBytes = createEscPosBarcodeLabelBytes(STATE.settings.shopName, name, price, barcode);
      const ok = await sendToPrinter(rawBytes);
      if (ok) alert("✅ Étiquette imprimée avec code-barres thermique !");
    });
  }

  // Test Bluetooth Print Button in Settings
  const btnTestBle = document.getElementById('btnTestBlePrint');
  if (btnTestBle) {
    btnTestBle.addEventListener('click', async () => {
      const dummyTicket = {
        number: 'TEST-' + Math.floor(1000 + Math.random() * 9000),
        date: new Date().toISOString(),
        items: [
          { productName: 'Article Test 1', qty: 1, price: 1500 },
          { productName: 'Article Test 2', qty: 2, price: 2500 }
        ],
        totalAmount: 6500,
        paymentMethod: 'cash'
      };
      const plainText = generateReceiptPlainText(dummyTicket);
      const rawBytes = createEscPosTicketBytes(plainText);
      const ok = await sendToPrinter(rawBytes);
      if (ok) alert("✅ Ticket de test imprimé avec succès !");
    });
  }

  // Service Worker Registration for PWA Offline
  if ('serviceWorker' in navigator) {
    navigator.serviceWorker.register('./service-worker.js').catch(console.error);
  }
});

// Other Tabs Rendering functions
function renderProducts() {
  const container = document.getElementById('productsListContainer');
  container.innerHTML = '';
  const search = (document.getElementById('prodSearchInput') ? document.getElementById('prodSearchInput').value : '').toLowerCase();
  
  const filtered = STATE.products.filter(p => p.name.toLowerCase().includes(search) || (p.barcode && p.barcode.includes(search)));

  filtered.forEach(p => {
    const card = document.createElement('div');
    card.className = 'card-group';
    card.style.display = 'flex';
    card.style.justifyContent = 'space-between';
    card.style.alignItems = 'center';
    card.style.cursor = 'pointer';
    card.innerHTML = `
      <div style="flex:1;">
        <div style="font-weight: 700; font-size: 15px;">${p.name}</div>
        <div style="font-size: 12px; color: var(--text-muted);">Stock : <strong style="color:${p.stock <= 5 ? 'var(--danger)' : 'var(--text-main)'};">${p.stock}</strong> | Code : ${p.barcode || 'Aucun'}</div>
        <div style="font-size: 14px; font-weight: 700; color: var(--primary); margin-top: 2px;">${p.salePrice.toLocaleString('fr-FR')} CFA</div>
      </div>
      <div style="display:flex; gap:6px; align-items:center;">
        <button class="icon-btn edit-prod-btn" style="color: var(--primary);">✏️</button>
      </div>
    `;
    card.addEventListener('click', () => {
      openProductModal(p);
    });
    container.appendChild(card);
  });
}

function renderHistory() {
  const container = document.getElementById('historyListContainer');
  container.innerHTML = '';
  let totalPeriod = 0;

  STATE.tickets.forEach(t => {
    totalPeriod += t.totalAmount;
    const card = document.createElement('div');
    card.className = 'card-group';
    card.style.marginBottom = '10px';
    card.style.cursor = 'pointer';
    card.innerHTML = `
      <div style="display: flex; justify-content: space-between; align-items: center;">
        <div>
          <div style="font-weight: 700; font-size: 14px;">${t.number}</div>
          <div style="font-size: 11px; color: var(--text-muted);">${new Date(t.date).toLocaleString('fr-FR')}</div>
        </div>
        <div style="font-size: 16px; font-weight: 700; color: var(--primary);">${t.totalAmount.toLocaleString('fr-FR')} CFA</div>
      </div>
    `;
    card.addEventListener('click', () => {
      document.getElementById('receiptPaperContent').innerHTML = generateReceiptHtml(t);
      document.getElementById('modalReceipt').classList.add('active');
    });
    container.appendChild(card);
  });

  document.getElementById('historyTotalSales').textContent = totalPeriod.toLocaleString('fr-FR') + ' CFA';
}

function renderReports() {
  const z = getZReportData();
  const stockVal = STATE.products.reduce((sum, p) => sum + (p.stock * p.salePrice), 0);
  document.getElementById('reportRevenue').textContent = z.totalSales.toLocaleString('fr-FR') + ' CFA';
  document.getElementById('reportProfit').textContent = (z.totalSales - z.totalExpenses).toLocaleString('fr-FR') + ' CFA';
  document.getElementById('reportStockValue').textContent = stockVal.toLocaleString('fr-FR') + ' CFA';

  // Render expenses list
  const expContainer = document.getElementById('expensesListContainer');
  if (expContainer) {
    expContainer.innerHTML = '';
    if (!STATE.expenses || STATE.expenses.length === 0) {
      expContainer.innerHTML = '<div style="font-size:12px; color:var(--text-muted); padding:8px 0;">Aucune dépense enregistrée</div>';
    } else {
      STATE.expenses.forEach(e => {
        const row = document.createElement('div');
        row.style.display = 'flex';
        row.style.justifyContent = 'space-between';
        row.style.padding = '6px 0';
        row.style.borderBottom = '1px solid var(--border)';
        row.style.fontSize = '13px';
        row.innerHTML = `
          <div><span>${e.reason}</span> <span style="font-size:10px; color:var(--text-muted);">${new Date(e.date).toLocaleTimeString('fr-FR')}</span></div>
          <div style="font-weight:700; color:var(--danger);">-${e.amount.toLocaleString('fr-FR')} CFA</div>
        `;
        expContainer.appendChild(row);
      });
    }
  }
}

function renderCustomers() {
  const container = document.getElementById('customersListContainer');
  container.innerHTML = '';
  let totalDebt = 0;
  STATE.customers.forEach(c => {
    totalDebt += c.debt;
    const card = document.createElement('div');
    card.className = 'card-group';
    card.style.display = 'flex';
    card.style.justifyContent = 'space-between';
    card.style.alignItems = 'center';
    card.innerHTML = `
      <div>
        <div style="font-weight: 700; font-size: 15px;">${c.name}</div>
        <div style="font-size: 12px; color: var(--text-muted);">${c.phone || 'Pas de numéro'}</div>
      </div>
      <div style="text-align: right;">
        <div style="font-size: 11px; color: var(--text-muted);">Dette</div>
        <div style="font-size: 15px; font-weight: 700; color: ${c.debt > 0 ? 'var(--danger)' : 'var(--success)'};">${c.debt.toLocaleString('fr-FR')} CFA</div>
      </div>
    `;
    container.appendChild(card);
  });
  document.getElementById('customerTotalDebt').textContent = totalDebt.toLocaleString('fr-FR') + ' CFA';
}

function renderTools() {
  const select = document.getElementById('toolProductSelect');
  select.innerHTML = '<option value="">-- Personnalisé --</option>';
  STATE.products.forEach(p => {
    select.innerHTML += `<option value="${p.id}">${p.name} (${p.salePrice} CFA)</option>`;
  });
  select.onchange = (e) => {
    const prod = STATE.products.find(p => p.id === e.target.value);
    if (prod) {
      document.getElementById('toolNameInput').value = prod.name;
      document.getElementById('toolPriceInput').value = prod.salePrice;
      document.getElementById('toolBarcodeInput').value = prod.barcode || '';
      document.getElementById('lblName').textContent = prod.name;
      document.getElementById('lblPrice').textContent = prod.salePrice + ' CFA';
      document.getElementById('lblSerial').textContent = prod.barcode || '';
    }
  };
}

function renderSettings() {
  document.getElementById('setShopName').value = STATE.settings.shopName;
  document.getElementById('setShopAddress').value = STATE.settings.shopAddress;
  document.getElementById('setShopPhone').value = STATE.settings.shopPhone;
  document.getElementById('setSellerName').value = STATE.settings.sellerName;
  document.getElementById('setReceiptFooter').value = STATE.settings.receiptFooter;
  document.getElementById('setShowSignature').checked = STATE.settings.showPublisherSignature;
  document.getElementById('setTaxEnabled').checked = STATE.settings.taxEnabled;
  document.getElementById('setAdminPin').value = STATE.settings.adminPin;
  if (document.getElementById('setPrinterWidth')) {
    document.getElementById('setPrinterWidth').value = STATE.settings.printerWidth || "58";
  }

  // Update License Status Card
  const devIdEl = document.getElementById('settingsDevId');
  if (devIdEl) devIdEl.textContent = getDeviceId();

  const badge = document.getElementById('settingsLicenseStatusBadge');
  if (badge) {
    if (isLicenseActive()) {
      const exp = STATE.settings.licenseExpiryDate ? ` (${new Date(STATE.settings.licenseExpiryDate).toLocaleDateString('fr-FR')})` : ' (À vie)';
      badge.textContent = `✅ Active - ${STATE.settings.licenseType || 'Commerciale'}${exp}`;
      badge.style.background = '#e8f5e9';
      badge.style.color = '#2e7d32';
    } else {
      badge.textContent = '❌ Non Activée / Expirée';
      badge.style.background = '#ffebee';
      badge.style.color = '#c62828';
    }
  }
}
