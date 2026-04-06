# MailCleanup Uygulama Analizi (Akış + Teknik)

Tarih: 2026-04-05  
Kapsam: `app/src/main` altındaki mevcut kod ve ekran akışları

## 1) Mevcut Ürün Akışı

### 1.1 Giriş / Yetkilendirme
- `MainActivity` içinde Credential Manager + Google Identity Authorization akışı var.
- Sessiz yetki kontrolü (`onStart`) ile cached hesap varsa otomatik devam deneniyor.
- Başarılı yetki sonrası `MainViewModel.startSubscriptionScan(account)` çağrılıyor ve liste ekranına geçiliyor.

### 1.2 Dashboard (Subscription List)
- `SubscriptionListFragment` içinde Paging 3 ile abonelik listesi yükleniyor.
- Arama, filtre (All/Newsletters/Promotions), sıralama (Most Frequent/A-Z) var.
- Satır bazlı unsubscribe, toplu seçim + toplu unsubscribe, undo snackbar akışı var.
- Detay ekranına geçiş bağlı.

### 1.3 Search
- `SearchFragment` listeyi query ile filtreliyor.
- Sonuç sayısı (`matches`) güncelleniyor.
- Sonuçtan detay ekranına geçiş bağlı.

### 1.4 Detail
- `SubscriptionDetailFragment` metrik kartları + aksiyonlar (`Unsubscribe`, `Delete All Emails`) içeriyor.
- Alt menü tıklamaları bağlanmış.
- `Unsubscribe` ve `Delete` için özel modal akışları mevcut.

### 1.5 Modal Akışları
- `UnsubscribeConfirmDialogFragment`: "Don't show this again" tercihi kalıcı tutuluyor.
- `DeleteConfirmDialogFragment`: silme için ayrı onay modalı var.

---

## 2) Teknik Durum Özeti

### Güçlü Taraflar
- Hilt DI aktif, repository/use-case wiring mevcut.
- `MainUiState` + `MainUiEvent` ayrımı uygulanmış.
- `repeatOnLifecycle` ile lifecycle-safe collect var.
- Domain error normalization (`DomainError`) ve typed result (`DomainResult`) var.
- Paging 3 geçişi yapılmış ve duplicate kayıt için cross-page dedup uygulanmış.
- Gmail rate-limit için adaptif throttle eklenmiş.

### Zayıf Taraflar
- View tabanlı UI büyüyor; ekran/akış karmaşıklığı arttı.
- Bazı iş kuralları UI/ViewModel içinde kalıyor (tam domain merkezli değil).
- Navigation resmi bir çatıya (Jetpack Navigation) oturmuyor.
- Gözlemlenebilirlik (structured logging/telemetry) düşük.
- Test yok (unit/instrumentation boş).

---

## 3) Kritik Eksikler (Önceliklendirilmiş)

## P0 (yüksek etki / kısa vadede yapılmalı)

1. **Gerçek delete/unsubscribe execution gap**
- Durum: Modallar onay sonrası çoğunlukla snackbar/queue mesajı veriyor.
- Risk: Kullanıcı "işlem oldu" sanıp veri tarafında beklediğini bulamayabilir.
- Öneri: Detail modalları doğrudan use-case'lere bağlanmalı; başarılı/başarısız durum UI'da net gösterilmeli.

2. **Navigation back-stack yönetimi kırılgan**
- Durum: Fragment transaction'lar manuel (`replace/addToBackStack`).
- Risk: Karmaşık senaryolarda geri tuşu ve state restore hataları.
- Öneri: Jetpack Navigation + Safe Args geçişi.

3. **Authentication state güvenliği**
- Durum: hesap bilgisi `SharedPreferences`'ta düz tutuluyor.
- Risk: hassas kullanıcı bilgisi için zayıf saklama.
- Öneri: EncryptedSharedPreferences veya token/account abstraction.

4. **AppDatabase singleton oluşturma yöntemi DI dışında**
- Durum: `AppDatabase.getDatabase(context)` manuel singleton.
- Risk: testability ve lifecycle yönetimi zayıflar.
- Öneri: Room DB + DAO provision tamamen Hilt modülünden verilsin.

## P1 (orta-yüksek etki)

5. **Offline-first strateji kısmi**
- Durum: kısa süreli window cache var ama kullanıcıya belirgin "cache/stale" davranışı yok.
- Öneri: local snapshot göster + background refresh + stale indicator.

6. **Domain model mutability**
- Durum: `Subscription.messageIds` mutable list.
- Risk: yan etki, race/debug zorluğu.
- Öneri: immutable model + dönüşüm noktalarında copy.

7. **Undo + paging edge-case netliği**
- Durum: hide/unhide + pending job mantığı var ama sayfalama/filtre/sort kombinasyonlarında regression riski yüksek.
- Öneri: deterministic state machine veya en azından reducer tabanlı event işleme.

8. **Rate-limit için tek merkez policy yok**
- Durum: remote data source içinde özel throttle var, genellenmemiş.
- Öneri: retry/backoff policy abstraction (shared helper + metrics).

9. **Performans: ağır filter işlemleri client-side**
- Durum: search/filter, paging stream üstünde sık uygulanıyor.
- Risk: büyük listede UI gecikmesi.
- Öneri: debounce + query-based remote/local indexing + daha erken filtreleme.

## P2 (orta)

10. **Observability eksik**
- Durum: `Log.d/e` var ama event-id/correlation yok.
- Öneri: structured logger + operasyon metrikleri (sign-in success rate, API error buckets, retry counts).

11. **Localization tutarlılığı**
- Durum: TR/EN birlikte kullanılıyor, string setleri tam hizalı değil.
- Öneri: `values-tr`/`values-en` parity checklist.

12. **UI standardizasyonu**
- Durum: çok sayıda özel drawable/layout var; token sistemi sınırlı.
- Öneri: spacing/typography/color tokenlarını merkezi hale getir.

---

## 4) Mimari Değerlendirme

Mevcut yapı 3 katman yönünde ilerlemiş durumda:
- Domain: model/repository interface/use-case
- Data: repository impl + local/remote source
- UI: Activity/Fragment/ViewModel

Ancak aşağıdaki noktalar mimariyi halen zayıflatıyor:
- Navigation ve state orchestration UI katmanında yoğun.
- DB provision/data lifecycle bir kısmı data source içinde gömülü.
- Bazı feature davranışları use-case yerine UI'da toplanmış.

---

## 5) Teknik Borç / Risk Notları

- `sourceCompatibility = 1.8` + JDK21 uyarıları var (build temiz ama teknik borç).
- Test klasörleri boş; refactor güvenliği düşük.
- CI pipeline kaldırıldığı için kalite kapısı yok (build/lint/test otomasyonu yok).

---

## 6) Önerilen Yol Haritası

## Faz 1 (hızlı stabilizasyon)
1. Detail modallarını gerçek use-case execution'a bağla.
2. DB/DAO provision'ı Hilt modüle taşı.
3. Search/filter için debounce + minimal perf optimizasyonu.
4. Kritik akışlara crash-safe guard (null account, detached fragment, duplicate click).

## Faz 2 (mimari sıkılaştırma)
1. Jetpack Navigation geçişi.
2. Immutable domain model dönüşümü.
3. Retry/backoff policy'yi shared bileşen yap.

## Faz 3 (kalite altyapısı)
1. Unit test: ViewModel + repository error mapping.
2. Integration test: sign-in -> list -> detail -> modal akışları.
3. Basit CI: assemble + test (lint opsiyonel).

---

## 7) Sonuç

Uygulama, işlevsel MVP seviyesini geçmiş ve ciddi miktarda UI/akış implementasyonu tamamlanmış durumda.  
En büyük açıklar artık "özellik eksikliği" değil, "davranışın garanti altına alınması" (navigation-state consistency, real action wiring, test/observability).

Kısa vadede en çok değer üretecek adımlar:
- delete/unsubscribe aksiyonlarını uçtan uca kesinleştirmek,
- navigation/state akışını standartlaştırmak,
- minimum test + telemetry tabanı kurmak.
