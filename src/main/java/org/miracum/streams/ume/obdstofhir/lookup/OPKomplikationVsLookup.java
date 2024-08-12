package org.miracum.streams.ume.obdstofhir.lookup;

import java.util.HashMap;

public class OPKomplikationVsLookup {

  private static final HashMap<String, String> lookup =
      new HashMap<>() {
        {
          put("N", "Nein");
          put("U", "Unbekannt");
          put("ABD", "Abszeß in einem Drainagekanal");
          put("ABS", "Abszeß, intraabdominaler oder intrathorakaler");
          put("ASF", "Abszeß, subfaszialer");
          put("ANI", "Akute Niereninsuffizienz");
          put("AEP", "Alkoholentzugspsychose");
          put("ALR", "Allergische Reaktion ohne Schocksymptomatik");
          put("ANS", "Anaphylaktischer Schock");
          put("AEE", "Anastomoseninsuffizienz einer Enterostomie");
          put("API", "Apoplektischer Insult");
          put("BIF", "Biliäre Fistel");
          put("BOG", "Blutung, obere gastrointestinale (z.B \"Stressulkus\")");
          put("BOE", "Bolusverlegung eines Endotubus");
          put("BSI", "Bronchusstumpfinsuffizienz");
          put("CHI", "Cholangitis");
          put("DAI", "Darmanastomoseinsuffizienz");
          put("DPS", "Darmpassagestörungen (z.B. protrahierte Atonie, Subileus, Ileus)");
          put("DIC", "Disseminierte intravasale Koagulopathie");
          put("DEP", "Drogenentzugspsychose");
          put("DLU", "Druck- und Lagerungsschäden, z.B. Dekubitalulzera");
          put("DSI", "Duodenalstumpfinsuffizienz");
          put("ENF", "Enterale Fistel");
          put("GER", "Gerinnungsstörung");
          put("HEM", "Hämatemesis");
          put("HUR", "Hämaturie");
          put("HAE", "Hämorrhagischer Schock");
          put("HFI", "Harnfistel");
          put("HNK", "Hautnekrose im Operationsbereich");
          put("HZI", "Herzinsuffizienz");
          put("HRS", "Herzrhythmusstörungen");
          put("HNA", "Hirnnervenausfälle");
          put("HOP", "Hirnorganisches Psychosyndrom (z.B. \"Durchgangssyndrom\")");
          put("HYB", "Hyperbilirubinämie");
          put("HYF", "Hypopharynxfistel");
          put("IFV", "Ileofemorale Venenthrombose");
          put("KAS", "Kardiogener Schock");
          put("KES", "Komplikationen einer Stomaanlage");
          put(
              "KIM",
              "Komplikation eines Implantates (Gefäßprothese, Totalendoprothese, Katheter), z.B. Dislokation");
          put("KRA", "Krampfanfall");
          put("KDS", "Kurzdarmsyndrom");
          put("LEV", "Leberversagen");
          put("LOE", "Lungenödem");
          put("LYF", "Lymphfistel");
          put("LYE", "Lymphozele");
          put("MES", "Magenentleerungsstörung");
          put("MIL", "Mechanischer Ileus");
          put("MED", "Mediastinitis");
          put("MAT", "Mesenterialarterien- oder -venenthrombose");
          put("MYI", "Myokardinfarkt");
          put("RNB", "Nachblutung, revisionsbedürftig, anderweitig nicht erwähnt");
          put("NAB", "Nachblutung, nicht revisionsbedürftig, anderweitig nicht erwähnt");
          put("NIN", "Nahtinsuffizienz, anderweitig nicht erwähnt");
          put("OES", "Ösophagitis");
          put("OSM", "Osteitis, Osteomyelitis");
          put("PAF", "Pankreasfistel");
          put("PIT", "Pankreatitis");
          put("PAB", "Peranale Blutung");
          put("PPA", "Periphere Parese");
          put("PAV", "Peripherer arterieller Verschluß (Embolie, Thrombose)");
          put("PER", "Peritonitis");
          put("PLB", "Platzbauch");
          put("PEY", "Pleuraempyem");
          put("PLE", "Pleuraerguß");
          put("PMN", "Pneumonie");
          put("PNT", "Pneumothorax");
          put("PDA", "Protrahierte Darmatonie (paralytischer Ileus)");
          put("PAE", "Pulmonalarterienembolie");
          put("RPA", "Rekurrensparese");
          put("RIN", "Respiratorische Insuffizienz");
          put("SKI", "Septische Komplikation eines Implantates");
          put("SES", "Septischer Schock");
          put("SFH", "Störungen des Flüssigkeits-, Elektrolyt- und Säurebasenhaushaltes");
          put("SON", "Sonstige Komplikation");
          put("STK", "Stomakomplikation (z.B. Blutung, Nekrose, Stenose)");
          put("TZP", "Thrombozytopenie");
          put(
              "TIA",
              "TIA (transitorische ischämische Attacke) oder RIND (reversibles ischämisches neurologisches Defizit)");
          put("TRZ", "Transfusionszwischenfall");
          put("WUH", "Wundhämatom (konservativ therapiert)");
          put("WSS", "Wundheilungsstörung, subkutane");
        }
      };

  public final String lookupOPKomplikationVSDisplay(String code) {
    return lookup.get(code);
  }
}
