ALTER SESSION SET CONTAINER = FREEPDB1;
create table DWH_ROUTINE.STG_ONKOSTAR_LKR_MELDUNG_EXPORT
(
    ID              NUMBER(10),
    LKR_MELDUNG     NUMBER(10) not null,
    LKR_EXPORT      NUMBER(10) not null,
    TYP             NUMBER(10) not null,
    XML_DATEN       CLOB,
    VERSIONSNUMMER  NUMBER(10),
    REFERENZ_NUMMER VARCHAR2(50 char)
)
    /


INSERT INTO DWH_ROUTINE.STG_ONKOSTAR_LKR_MELDUNG_EXPORT (ID, LKR_MELDUNG, LKR_EXPORT, TYP, XML_DATEN, VERSIONSNUMMER,
                                                         REFERENZ_NUMMER)
VALUES (1, 1, 1, 1, to_clob('<?xml version="1.0" encoding="UTF-8"?><oBDS xmlns="http://www.basisdatensatz.de/oBDS/XML" Schema_Version="3.0.2">
                                   <Absender Absender_ID="12345/12345" Software_ID="ONKOSTAR" Software_Version="2.13.0" Installations_ID="2">
                                       <Bezeichnung>CCC</Bezeichnung>
                                       <Ansprechpartner>Dr. Absender</Ansprechpartner>
                                       <Anschrift>musterstrasse 1, 123456 Musterstadt</Anschrift>
                                   </Absender>
                                   <Meldedatum>2024-11-30</Meldedatum>
                                   <Menge_Patient>
                                       <Patient Patient_ID="9876543">
                                           <Patienten_Stammdaten>
                                               <Versichertendaten_GKV>
                                                   <IKNR>222333444</IKNR>
                                                   <GKV_Versichertennummer>X121212121</GKV_Versichertennummer>
                                               </Versichertendaten_GKV>
                                               <Nachname>Musterfrau</Nachname>
                                               <Vornamen>Maxi</Vornamen>
                                               <Geschlecht>W</Geschlecht>
                                               <Geburtsdatum Datumsgenauigkeit="E">1960-01-16</Geburtsdatum>
                                               <Adresse>
                                                   <Strasse>Musterweg 11</Strasse>
                                                   <Land>DE</Land>
                                                   <PLZ>12345</PLZ>
                                                   <Ort>Musterstadt</Ort>
                                               </Adresse>
                                           </Patienten_Stammdaten>
                                           <Menge_Meldung>
                                               <Meldung Meldung_ID="2103B5C5-123123/12312" Melder_ID="123123/12312">
                                                   <Meldebegruendung>I</Meldebegruendung>
                                                   <Zertifizierung>3</Zertifizierung>
                                                   <Eigene_Leistung>N</Eigene_Leistung>
                                                   <Tumorzuordnung Tumor_ID="1">
                                                       <Primaertumor_ICD>
                                                           <Code>C43.5</Code>
                                                           <Version>10 2023 GM</Version>
                                                       </Primaertumor_ICD>
                                                       <Diagnosedatum Datumsgenauigkeit="E">2023-10-11</Diagnosedatum>
                                                       <Seitenlokalisation>T</Seitenlokalisation>
                                                       <Morphologie_ICD_O>
                                                           <Code>8720/3</Code>
                                                           <Version>33</Version>
                                                       </Morphologie_ICD_O>
                                                   </Tumorzuordnung>') || to_clob('
                                                   <Diagnose>
                                                       <Primaertumor_Topographie_ICD_O>
                                                           <Code>C44.9</Code>
                                                           <Version>33</Version>
                                                       </Primaertumor_Topographie_ICD_O>
                                                       <Primaertumor_Topographie_Freitext>Vulva</Primaertumor_Topographie_Freitext>
                                                       <Diagnosesicherung>7</Diagnosesicherung>
                                                       <Histologie>
                                                           <Morphologie_ICD_O>
                                                               <Code>8720/3</Code>
                                                               <Version>33</Version>
                                                           </Morphologie_ICD_O>
                                                           <Morphologie_ICD_O>
                                                               <Code>8720/2</Code>
                                                               <Version>33</Version>
                                                           </Morphologie_ICD_O>
                                                           <Grading>U</Grading>
                                                       </Histologie>
                                                       <cTNM ID="4444888c">
                                                           <Datum>2024-02-09</Datum>
                                                           <Version>8</Version>
                                                           <c_p_u_Praefix_T>p</c_p_u_Praefix_T>
                                                           <T>2b</T>
                                                           <c_p_u_Praefix_N>p</c_p_u_Praefix_N>
                                                           <N>1b</N>
                                                           <c_p_u_Praefix_M>p</c_p_u_Praefix_M>
                                                           <M>1b</M>
                                                       </cTNM>
                                                       <pTNM ID="5555555">
                                                           <Datum>2023-10-11</Datum>
                                                           <Version>8</Version>
                                                           <c_p_u_Praefix_T>p</c_p_u_Praefix_T>
                                                           <T>is</T>
                                                       </pTNM>
                                                       <Menge_Weitere_Klassifikation>
                                                           <Weitere_Klassifikation>
                                                               <Datum Datumsgenauigkeit="E">2023-12-09</Datum>
                                                               <Name>AJCC</Name>
                                                               <Stadium>IV</Stadium>
                                                           </Weitere_Klassifikation>
                                                       </Menge_Weitere_Klassifikation>
                                                       <Allgemeiner_Leistungszustand>U</Allgemeiner_Leistungszustand>
                                                       <Modul_Allgemein>
                                                           <Studienteilnahme>
                                                               <NU>N</NU>
                                                           </Studienteilnahme>
                                                       </Modul_Allgemein>
                                                   </Diagnose>') || to_clob('
                                               </Meldung>
                                           </Menge_Meldung>
                                       </Patient>
                                   </Menge_Patient>
                                   <Menge_Melder>
                                       <Melder ID="123123/12312">
                                           <KH_Abt_Station_Praxis>CCC</KH_Abt_Station_Praxis>
                                           <Anschrift>Musterstrasse 1</Anschrift>
                                           <PLZ>12345</PLZ>
                                           <Ort>Musterstadt</Ort>
                                       </Melder>
                                   </Menge_Melder>
                               </oBDS>'), 1, '1055555999');


INSERT INTO DWH_ROUTINE.STG_ONKOSTAR_LKR_MELDUNG_EXPORT (ID, LKR_MELDUNG, LKR_EXPORT, TYP, XML_DATEN, VERSIONSNUMMER,
                                                         REFERENZ_NUMMER)
VALUES (2,2, 1, 1, to_clob('<?xml version="1.0" encoding="UTF-8"?><oBDS xmlns="http://www.basisdatensatz.de/oBDS/XML" Schema_Version="3.0.2">
                                   <Absender Absender_ID="12345/12345" Software_ID="ONKOSTAR" Software_Version="2.13.0" Installations_ID="2">
                                       <Bezeichnung>CCC</Bezeichnung>
                                       <Ansprechpartner>Dr. Absender</Ansprechpartner>
                                       <Anschrift>musterstrasse 1, 123456 Musterstadt</Anschrift>
                                   </Absender>
                                   <Meldedatum>2024-11-30</Meldedatum>
                                   <Menge_Patient>
                                       <Patient Patient_ID="9876543">
                                           <Patienten_Stammdaten>
                                               <Versichertendaten_GKV>
                                                   <IKNR>222333444</IKNR>
                                                   <GKV_Versichertennummer>X121212121</GKV_Versichertennummer>
                                               </Versichertendaten_GKV>
                                               <Nachname>Musterfrau</Nachname>
                                               <Vornamen>Maxi</Vornamen>
                                               <Geschlecht>W</Geschlecht>
                                               <Geburtsdatum Datumsgenauigkeit="E">1960-01-16</Geburtsdatum>
                                               <Adresse>
                                                   <Strasse>Musterweg 11</Strasse>
                                                   <Land>DE</Land>
                                                   <PLZ>12345</PLZ>
                                                   <Ort>Musterstadt</Ort>
                                               </Adresse>
                                           </Patienten_Stammdaten>
                                           <Menge_Meldung>') || to_clob('
                                               <Meldung Meldung_ID="0002A5D5-12345/12345" Melder_ID="12345/12345">
                                                   <Meldebegruendung>I</Meldebegruendung>
                                                   <Eigene_Leistung>J</Eigene_Leistung>
                                                   <Tumorzuordnung Tumor_ID="1">
                                                       <Primaertumor_ICD>
                                                           <Code>C43.5</Code>
                                                           <Version>10 2023 GM</Version>
                                                       </Primaertumor_ICD>
                                                       <Diagnosedatum Datumsgenauigkeit="E">2023-10-11</Diagnosedatum>
                                                       <Seitenlokalisation>T</Seitenlokalisation>
                                                       <Morphologie_ICD_O>
                                                           <Code>8720/3</Code>
                                                           <Version>33</Version>
                                                       </Morphologie_ICD_O>
                                                   </Tumorzuordnung>
                                                   <Tumorkonferenz Tumorkonferenz_ID="3187120">
                                                       <Meldeanlass>behandlungsbeginn</Meldeanlass>
                                                       <Datum Datumsgenauigkeit="E">2023-12-06</Datum>
                                                       <Typ>praeth</Typ>
                                                   </Tumorkonferenz>
                                               </Meldung>
                                           </Menge_Meldung>
                                       </Patient>
                                   </Menge_Patient>') || to_clob('
                                   <Menge_Melder>
                                       <Melder ID="12345/12345">
                                           <KH_Abt_Station_Praxis>CCC</KH_Abt_Station_Praxis>
                                           <Arztname>Dr. Absender</Arztname>
                                           <Anschrift>Musterstrasse 1</Anschrift>
                                           <PLZ>12345</PLZ>
                                           <Ort>Musterstadt</Ort>
                                       </Melder>
                                   </Menge_Melder>
                               </oBDS>
'), 1, '1055555999');

INSERT INTO DWH_ROUTINE.STG_ONKOSTAR_LKR_MELDUNG_EXPORT (ID, LKR_MELDUNG, LKR_EXPORT, TYP, XML_DATEN, VERSIONSNUMMER,
                                                         REFERENZ_NUMMER)
VALUES (3, 3, 1, 1, to_clob('<?xml version="1.0" encoding="UTF-8"?><oBDS xmlns="http://www.basisdatensatz.de/oBDS/XML" Schema_Version="3.0.2">
                                  <Absender Absender_ID="12345/12345" Software_ID="ONKOSTAR" Software_Version="2.13.0" Installations_ID="2">
                                      <Bezeichnung>CCC</Bezeichnung>
                                      <Ansprechpartner>Dr. Absender</Ansprechpartner>
                                      <Anschrift>musterstrasse 1, 123456 Musterstadt</Anschrift>
                                  </Absender>
                                  <Meldedatum>2024-11-30</Meldedatum>
                                  <Menge_Patient>
                                      <Patient Patient_ID="9876543">
                                          <Patienten_Stammdaten>
                                              <Versichertendaten_GKV>
                                                  <IKNR>222333444</IKNR>
                                                  <GKV_Versichertennummer>X121212121</GKV_Versichertennummer>
                                              </Versichertendaten_GKV>
                                              <Nachname>Musterfrau</Nachname>
                                              <Vornamen>Maxi</Vornamen>
                                              <Geschlecht>W</Geschlecht>
                                              <Geburtsdatum Datumsgenauigkeit="E">1960-01-16</Geburtsdatum>
                                              <Adresse>
                                                  <Strasse>Musterweg 11</Strasse>
                                                  <Land>DE</Land>
                                                  <PLZ>12345</PLZ>
                                                  <Ort>Musterstadt</Ort>
                                              </Adresse>
                                          </Patienten_Stammdaten>
                                          <Menge_Meldung>') || to_clob('
                                              <Meldung Meldung_ID="0003A5D6-12345/12345" Melder_ID="12345/12345">
                                                  <Meldebegruendung>I</Meldebegruendung>
                                                  <Eigene_Leistung>J</Eigene_Leistung>
                                                  <Tumorzuordnung Tumor_ID="1">
                                                      <Primaertumor_ICD>
                                                          <Code>C43.5</Code>
                                                          <Version>10 2023 GM</Version>
                                                      </Primaertumor_ICD>
                                                      <Diagnosedatum Datumsgenauigkeit="E">2023-10-11</Diagnosedatum>
                                                      <Seitenlokalisation>T</Seitenlokalisation>
                                                      <Morphologie_ICD_O>
                                                          <Code>8720/3</Code>
                                                          <Version>33</Version>
                                                      </Morphologie_ICD_O>
                                                  </Tumorzuordnung>
                                                  <Tumorkonferenz Tumorkonferenz_ID="9191911">
                                                      <Meldeanlass>behandlungsbeginn</Meldeanlass>
                                                      <Datum Datumsgenauigkeit="E">2023-12-08</Datum>
                                                      <Typ>praeth</Typ>
                                                      <Therapieempfehlung>
                                                          <Menge_Typ_Therapieempfehlung>
                                                              <Typ_Therapieempfehlung>OP</Typ_Therapieempfehlung>
                                                          </Menge_Typ_Therapieempfehlung>
                                                          <Abweichung_Patientenwunsch>U</Abweichung_Patientenwunsch>
                                                      </Therapieempfehlung>
                                                  </Tumorkonferenz>
                                              </Meldung>
                                          </Menge_Meldung>') || to_clob('
                                      </Patient>
                                  </Menge_Patient>
                                  <Menge_Melder>
                                      <Melder ID="12345/12345">
                                          <KH_Abt_Station_Praxis>CCC</KH_Abt_Station_Praxis>
                                          <Arztname>Dr. Absender</Arztname>
                                          <Anschrift>Musterstrasse 1</Anschrift>
                                          <PLZ>12345</PLZ>
                                          <Ort>Musterstadt</Ort>
                                      </Melder>
                                  </Menge_Melder>
                              </oBDS>
'), 1, '1055555999');

INSERT INTO DWH_ROUTINE.STG_ONKOSTAR_LKR_MELDUNG_EXPORT (ID, LKR_MELDUNG, LKR_EXPORT, TYP, XML_DATEN, VERSIONSNUMMER,
                                                         REFERENZ_NUMMER)
VALUES (4, 4, 1, 1, to_clob('<?xml version="1.0" encoding="UTF-8"?><oBDS xmlns="http://www.basisdatensatz.de/oBDS/XML" Schema_Version="3.0.2">
                                    <Absender Absender_ID="12345/12345" Software_ID="ONKOSTAR" Software_Version="2.13.0" Installations_ID="2">
                                        <Bezeichnung>CCC</Bezeichnung>
                                        <Ansprechpartner>Dr. Absender</Ansprechpartner>
                                        <Anschrift>musterstrasse 1, 123456 Musterstadt</Anschrift>
                                    </Absender>
                                    <Meldedatum>2024-11-30</Meldedatum>
                                    <Menge_Patient>
                                        <Patient Patient_ID="9876543">
                                            <Patienten_Stammdaten>
                                                <Versichertendaten_GKV>
                                                    <IKNR>222333444</IKNR>
                                                    <GKV_Versichertennummer>X121212121</GKV_Versichertennummer>
                                                </Versichertendaten_GKV>
                                                <Nachname>Musterfrau</Nachname>
                                                <Vornamen>Maxi</Vornamen>
                                                <Geschlecht>W</Geschlecht>
                                                <Geburtsdatum Datumsgenauigkeit="E">1960-01-16</Geburtsdatum>
                                                <Adresse>
                                                    <Strasse>Musterweg 11</Strasse>
                                                    <Land>DE</Land>
                                                    <PLZ>12345</PLZ>
                                                    <Ort>Musterstadt</Ort>
                                                </Adresse>
                                            </Patienten_Stammdaten>
                                            <Menge_Meldung>') || to_clob('
                                                <Meldung Meldung_ID="0001B5E5-222222/22222" Melder_ID="222222/22222">
                                                    <Meldebegruendung>I</Meldebegruendung>
                                                    <Eigene_Leistung>J</Eigene_Leistung>
                                                    <Tumorzuordnung Tumor_ID="1">
                                                        <Primaertumor_ICD>
                                                            <Code>C43.5</Code>
                                                            <Version>10 2023 GM</Version>
                                                        </Primaertumor_ICD>
                                                        <Diagnosedatum Datumsgenauigkeit="E">2023-10-20</Diagnosedatum>
                                                        <Seitenlokalisation>T</Seitenlokalisation>
                                                        <Morphologie_ICD_O>
                                                            <Code>8720/3</Code>
                                                            <Version>33</Version>
                                                        </Morphologie_ICD_O>
                                                    </Tumorzuordnung>
                                                    <OP OP_ID="1277663">
                                                        <Intention>P</Intention>
                                                        <Datum>2024-01-07</Datum>
                                                        <Menge_OPS>
                                                            <OPS>
                                                                <Code>5-98c.1</Code>
                                                                <Version>2023</Version>
                                                            </OPS>
                                                            <OPS>
                                                                <Code>5-322.h4</Code>
                                                                <Version>2023</Version>
                                                            </OPS>
                                                        </Menge_OPS>
                                                        <Histologie Histologie_ID="3365191">
                                                            <Tumor_Histologiedatum Datumsgenauigkeit="E">2024-01-07</Tumor_Histologiedatum>
                                                            <Histologie_EinsendeNr>NXP_H/2024/4548.100</Histologie_EinsendeNr>
                                                            <Morphologie_ICD_O>
                                                                <Code>8720/3</Code>
                                                                <Version>33</Version>
                                                            </Morphologie_ICD_O>
                                                            <Grading>T</Grading>
                                                        </Histologie>') || to_clob('
                                                        <TNM ID="4444888c">
                                                            <Datum>2024-02-09</Datum>
                                                            <Version>8</Version>
                                                            <c_p_u_Praefix_T>p</c_p_u_Praefix_T>
                                                            <T>X</T>
                                                            <c_p_u_Praefix_N>p</c_p_u_Praefix_N>
                                                            <N>1b</N>
                                                            <c_p_u_Praefix_M>p</c_p_u_Praefix_M>
                                                            <M>1b</M>
                                                        </TNM>
                                                        <Residualstatus>
                                                            <Lokale_Beurteilung_Residualstatus>RX</Lokale_Beurteilung_Residualstatus>
                                                        </Residualstatus>
                                                        <Komplikationen>
                                                            <Komplikation_nein_oder_unbekannt>N</Komplikation_nein_oder_unbekannt>
                                                        </Komplikationen>
                                                        <Menge_Operateur>
                                                            <Operateur>
                                                                <Nachname>OP_Nachname</Nachname>
                                                                <Vornamen>OP_Vorname</Vornamen>
                                                                <Hauptoperateur>J</Hauptoperateur>
                                                            </Operateur>
                                                            <Operateur>
                                                                <Nachname>OP_Nachname2</Nachname>
                                                                <Vornamen>OP_Vorname2</Vornamen>
                                                                <Hauptoperateur>N</Hauptoperateur>
                                                            </Operateur>
                                                        </Menge_Operateur>
                                                        <Modul_Allgemein>
                                                            <Studienteilnahme>
                                                                <NU>N</NU>
                                                            </Studienteilnahme>
                                                        </Modul_Allgemein>
                                                    </OP>
                                                </Meldung>
                                            </Menge_Meldung>') || to_clob('
                                        </Patient>
                                    </Menge_Patient>
                                    <Menge_Melder>
                                        <Melder ID="222222/22222">
                                            <KH_Abt_Station_Praxis>CCC</KH_Abt_Station_Praxis>
                                            <Anschrift>Musterstrasse 1</Anschrift>
                                            <PLZ>12345</PLZ>
                                            <Ort>Musterstadt</Ort>
                                        </Melder>
                                    </Menge_Melder>
                                </oBDS>
'), 1, '1055555999');

INSERT INTO DWH_ROUTINE.STG_ONKOSTAR_LKR_MELDUNG_EXPORT (ID, LKR_MELDUNG, LKR_EXPORT, TYP, XML_DATEN, VERSIONSNUMMER,
                                                         REFERENZ_NUMMER)
VALUES (5, 5, 1, 1, to_clob('<?xml version="1.0" encoding="UTF-8"?><oBDS xmlns="http://www.basisdatensatz.de/oBDS/XML" Schema_Version="3.0.2">
                                   <Absender Absender_ID="12345/12345" Software_ID="ONKOSTAR" Software_Version="2.13.0" Installations_ID="2">
                                       <Bezeichnung>CCC</Bezeichnung>
                                       <Ansprechpartner>Dr. Absender</Ansprechpartner>
                                       <Anschrift>musterstrasse 1, 123456 Musterstadt</Anschrift>
                                   </Absender>
                                   <Meldedatum>2024-11-30</Meldedatum>
                                   <Menge_Patient>
                                       <Patient Patient_ID="9876543">
                                           <Patienten_Stammdaten>
                                               <Versichertendaten_GKV>
                                                   <IKNR>222333444</IKNR>
                                                   <GKV_Versichertennummer>X121212121</GKV_Versichertennummer>
                                               </Versichertendaten_GKV>
                                               <Nachname>Musterfrau</Nachname>
                                               <Vornamen>Maxi</Vornamen>
                                               <Geschlecht>W</Geschlecht>
                                               <Geburtsdatum Datumsgenauigkeit="E">1960-01-16</Geburtsdatum>
                                               <Adresse>
                                                   <Strasse>Musterweg 11</Strasse>
                                                   <Land>DE</Land>
                                                   <PLZ>12345</PLZ>
                                                   <Ort>Musterstadt</Ort>
                                               </Adresse>
                                           </Patienten_Stammdaten>
                                           <Menge_Meldung>') || to_clob('
                                               <Meldung Meldung_ID="0003B5F5-321323/12345" Melder_ID="321323/12345">
                                                   <Meldebegruendung>I</Meldebegruendung>
                                                   <Eigene_Leistung>J</Eigene_Leistung>
                                                   <Tumorzuordnung Tumor_ID="1">
                                                       <Primaertumor_ICD>
                                                           <Code>C43.5</Code>
                                                           <Version>10 2023 GM</Version>
                                                       </Primaertumor_ICD>
                                                       <Diagnosedatum Datumsgenauigkeit="E">2023-08-20</Diagnosedatum>
                                                       <Seitenlokalisation>T</Seitenlokalisation>
                                                       <Morphologie_ICD_O>
                                                           <Code>8720/3</Code>
                                                           <Version>33</Version>
                                                       </Morphologie_ICD_O>
                                                   </Tumorzuordnung>
                                                   <OP OP_ID="3363368">
                                                       <Intention>K</Intention>
                                                       <Datum>2024-04-05</Datum>
                                                       <Menge_OPS>
                                                           <OPS>
                                                               <Code>5-714.5</Code>
                                                               <Version>2023</Version>
                                                           </OPS>
                                                           <OPS>
                                                               <Code>5-714.41</Code>
                                                               <Version>2023</Version>
                                                           </OPS>
                                                       </Menge_OPS>
                                                       <Histologie Histologie_ID="3848484">
                                                           <Tumor_Histologiedatum Datumsgenauigkeit="E">2024-02-05</Tumor_Histologiedatum>
                                                           <Histologie_EinsendeNr>NXP_H/2024/4444.200</Histologie_EinsendeNr>
                                                           <Morphologie_ICD_O>
                                                               <Code>8720/3</Code>
                                                               <Version>33</Version>
                                                           </Morphologie_ICD_O>
                                                           <Grading>T</Grading>
                                                       </Histologie>
                                                       <TNM ID="4444888c">
                                                           <Datum>2024-02-09</Datum>
                                                           <Version>8</Version>
                                                           <c_p_u_Praefix_T>p</c_p_u_Praefix_T>
                                                           <T>2a</T>
                                                           <c_p_u_Praefix_N>p</c_p_u_Praefix_N>
                                                           <N>1b</N>
                                                           <c_p_u_Praefix_M>p</c_p_u_Praefix_M>
                                                           <M>1b</M>
                                                       </TNM>') || to_clob('
                                                       <Residualstatus>
                                                           <Lokale_Beurteilung_Residualstatus>R1(is)</Lokale_Beurteilung_Residualstatus>
                                                       </Residualstatus>
                                                       <Komplikationen>
                                                           <Komplikation_nein_oder_unbekannt>N</Komplikation_nein_oder_unbekannt>
                                                       </Komplikationen>
                                                       <Modul_Allgemein>
                                                           <Studienteilnahme>
                                                               <NU>N</NU>
                                                           </Studienteilnahme>
                                                       </Modul_Allgemein>
                                                       <Modul_Malignes_Melanom>
                                                           <Tumordicke>1.1</Tumordicke>
                                                       </Modul_Malignes_Melanom>
                                                   </OP>
                                               </Meldung>
                                           </Menge_Meldung>
                                       </Patient>
                                   </Menge_Patient>
                                   <Menge_Melder>
                                       <Melder ID="321323/12345">
                                           <KH_Abt_Station_Praxis>CCC</KH_Abt_Station_Praxis>
                                           <Anschrift>Musterstrasse 1</Anschrift>
                                           <PLZ>12345</PLZ>
                                           <Ort>Musterstadt</Ort>
                                       </Melder>
                                   </Menge_Melder>
                               </oBDS>
'), 1, '1055555888');

INSERT INTO DWH_ROUTINE.STG_ONKOSTAR_LKR_MELDUNG_EXPORT (ID, LKR_MELDUNG, LKR_EXPORT, TYP, XML_DATEN, VERSIONSNUMMER,
                                                         REFERENZ_NUMMER)
VALUES (6, 6, 1, 1, to_clob('<?xml version="1.0" encoding="UTF-8"?><oBDS xmlns="http://www.basisdatensatz.de/oBDS/XML" Schema_Version="3.0.2">
                                    <Absender Absender_ID="12345/12345" Software_ID="ONKOSTAR" Software_Version="2.13.0" Installations_ID="2">
                                        <Bezeichnung>CCC</Bezeichnung>
                                        <Ansprechpartner>Dr. Absender</Ansprechpartner>
                                        <Anschrift>musterstrasse 1, 123456 Musterstadt</Anschrift>
                                    </Absender>
                                    <Meldedatum>2024-11-30</Meldedatum>
                                    <Menge_Patient>
                                        <Patient Patient_ID="9876543">
                                            <Patienten_Stammdaten>
                                                <Versichertendaten_GKV>
                                                    <IKNR>222333444</IKNR>
                                                    <GKV_Versichertennummer>X121212121</GKV_Versichertennummer>
                                                </Versichertendaten_GKV>
                                                <Nachname>Musterfrau</Nachname>
                                                <Vornamen>Maxi</Vornamen>
                                                <Geschlecht>W</Geschlecht>
                                                <Geburtsdatum Datumsgenauigkeit="E">1960-01-16</Geburtsdatum>
                                                <Adresse>
                                                    <Strasse>Musterweg 11</Strasse>
                                                    <Land>DE</Land>
                                                    <PLZ>12345</PLZ>
                                                    <Ort>Musterstadt</Ort>
                                                </Adresse>
                                            </Patienten_Stammdaten>
                                            <Menge_Meldung>') || to_clob('
                                                <Meldung Meldung_ID="0003D103-12345/12345" Melder_ID="12345/12345">
                                                    <Meldebegruendung>I</Meldebegruendung>
                                                    <Eigene_Leistung>J</Eigene_Leistung>
                                                    <Tumorzuordnung Tumor_ID="1">
                                                        <Primaertumor_ICD>
                                                            <Code>C43.5</Code>
                                                            <Version>10 2023 GM</Version>
                                                        </Primaertumor_ICD>
                                                        <Diagnosedatum Datumsgenauigkeit="E">2023-10-11</Diagnosedatum>
                                                        <Seitenlokalisation>T</Seitenlokalisation>
                                                        <Morphologie_ICD_O>
                                                            <Code>8720/3</Code>
                                                            <Version>33</Version>
                                                        </Morphologie_ICD_O>
                                                    </Tumorzuordnung>
                                                    <Tumorkonferenz Tumorkonferenz_ID="1237774">
                                                        <Meldeanlass>behandlungsende</Meldeanlass>
                                                        <Datum Datumsgenauigkeit="E">2024-01-26</Datum>
                                                        <Typ>postop</Typ>
                                                        <Therapieempfehlung>
                                                            <Menge_Typ_Therapieempfehlung>
                                                                <Typ_Therapieempfehlung>CH</Typ_Therapieempfehlung>
                                                                <Typ_Therapieempfehlung>OP</Typ_Therapieempfehlung>
                                                            </Menge_Typ_Therapieempfehlung>
                                                            <Abweichung_Patientenwunsch>N</Abweichung_Patientenwunsch>
                                                        </Therapieempfehlung>
                                                    </Tumorkonferenz>
                                                </Meldung>
                                            </Menge_Meldung>
                                        </Patient>
                                    </Menge_Patient>') || to_clob('
                                    <Menge_Melder>
                                        <Melder ID="12345/12345">
                                            <KH_Abt_Station_Praxis>CCC</KH_Abt_Station_Praxis>
                                            <Arztname>Dr. Absender</Arztname>
                                            <Anschrift>Musterstrasse 1</Anschrift>
                                            <PLZ>12345</PLZ>
                                            <Ort>Musterstadt</Ort>
                                        </Melder>
                                    </Menge_Melder>
                                </oBDS>
'), 1, '1055555888');


INSERT INTO DWH_ROUTINE.STG_ONKOSTAR_LKR_MELDUNG_EXPORT (ID, LKR_MELDUNG, LKR_EXPORT, TYP, XML_DATEN, VERSIONSNUMMER,
                                                         REFERENZ_NUMMER)
VALUES (7, 7, 1, 1, to_clob('<?xml version="1.0" encoding="UTF-8"?><oBDS xmlns="http://www.basisdatensatz.de/oBDS/XML" Schema_Version="3.0.2">
                                    <Absender Absender_ID="12345/12345" Software_ID="ONKOSTAR" Software_Version="2.13.0" Installations_ID="2">
                                        <Bezeichnung>CCC</Bezeichnung>
                                        <Ansprechpartner>Dr. Absender</Ansprechpartner>
                                        <Anschrift>musterstrasse 1, 123456 Musterstadt</Anschrift>
                                    </Absender>
                                    <Meldedatum>2024-11-30</Meldedatum>
                                    <Menge_Patient>
                                        <Patient Patient_ID="9876543">
                                            <Patienten_Stammdaten>
                                                <Versichertendaten_GKV>
                                                    <IKNR>222333444</IKNR>
                                                    <GKV_Versichertennummer>X121212121</GKV_Versichertennummer>
                                                </Versichertendaten_GKV>
                                                <Nachname>Musterfrau</Nachname>
                                                <Vornamen>Maxi</Vornamen>
                                                <Geschlecht>W</Geschlecht>
                                                <Geburtsdatum Datumsgenauigkeit="E">1960-01-16</Geburtsdatum>
                                                <Adresse>
                                                    <Strasse>Musterweg 11</Strasse>
                                                    <Land>DE</Land>
                                                    <PLZ>12345</PLZ>
                                                    <Ort>Musterstadt</Ort>
                                                </Adresse>
                                            </Patienten_Stammdaten>
                                            <Menge_Meldung>') || to_clob('
                                                <Meldung Meldung_ID="0003A603-555555/12345" Melder_ID="555555/12345">
                                                    <Meldebegruendung>I</Meldebegruendung>
                                                    <Eigene_Leistung>J</Eigene_Leistung>
                                                    <Tumorzuordnung Tumor_ID="1">
                                                        <Primaertumor_ICD>
                                                            <Code>C43.5</Code>
                                                            <Version>10 2023 GM</Version>
                                                        </Primaertumor_ICD>
                                                        <Diagnosedatum Datumsgenauigkeit="E">2023-10-11</Diagnosedatum>
                                                        <Seitenlokalisation>T</Seitenlokalisation>
                                                        <Morphologie_ICD_O>
                                                            <Code>8720/3</Code>
                                                            <Version>33</Version>
                                                        </Morphologie_ICD_O>
                                                    </Tumorzuordnung>
                                                    <SYST SYST_ID="5555555">
                                                        <Meldeanlass>behandlungsbeginn</Meldeanlass>
                                                        <Intention>K</Intention>
                                                        <Stellung_OP>A</Stellung_OP>
                                                        <Therapieart>CI</Therapieart>
                                                        <Beginn Datumsgenauigkeit="E">2024-02-12</Beginn>
                                                        <Menge_Substanz>
                                                            <Substanz>
                                                                <Bezeichnung>Ipilimumab</Bezeichnung>
                                                            </Substanz>
                                                        </Menge_Substanz>
                                                        <Nebenwirkungen>
                                                            <Grad_maximal2_oder_unbekannt>U</Grad_maximal2_oder_unbekannt>
                                                        </Nebenwirkungen>
                                                        <Modul_Allgemein>
                                                            <Sozialdienstkontakt>
                                                                <Datum>2024-03-21</Datum>
                                                            </Sozialdienstkontakt>
                                                            <Studienteilnahme>
                                                                <NU>N</NU>
                                                            </Studienteilnahme>
                                                        </Modul_Allgemein>
                                                    </SYST>
                                                </Meldung>') || to_clob('
                                            </Menge_Meldung>
                                        </Patient>
                                    </Menge_Patient>
                                    <Menge_Melder>
                                        <Melder ID="555555/12345">
                                            <KH_Abt_Station_Praxis>CCC</KH_Abt_Station_Praxis>
                                            <Anschrift>Musterstrasse 1</Anschrift>
                                            <PLZ>12345</PLZ>
                                            <Ort>Musterstadt</Ort>
                                        </Melder>
                                    </Menge_Melder>
                                </oBDS>
'), 1, '1055555888');


INSERT INTO DWH_ROUTINE.STG_ONKOSTAR_LKR_MELDUNG_EXPORT (ID, LKR_MELDUNG, LKR_EXPORT, TYP, XML_DATEN, VERSIONSNUMMER,
                                                         REFERENZ_NUMMER)
VALUES (8, 8, 1, 1, to_clob('<?xml version="1.0" encoding="UTF-8"?><oBDS xmlns="http://www.basisdatensatz.de/oBDS/XML" Schema_Version="3.0.2">
                                    <Absender Absender_ID="12345/12345" Software_ID="ONKOSTAR" Software_Version="2.13.0" Installations_ID="2">
                                        <Bezeichnung>CCC</Bezeichnung>
                                        <Ansprechpartner>Dr. Absender</Ansprechpartner>
                                        <Anschrift>musterstrasse 1, 123456 Musterstadt</Anschrift>
                                    </Absender>
                                    <Meldedatum>2024-11-30</Meldedatum>
                                    <Menge_Patient>
                                        <Patient Patient_ID="9876543">
                                            <Patienten_Stammdaten>
                                                <Versichertendaten_GKV>
                                                    <IKNR>222333444</IKNR>
                                                    <GKV_Versichertennummer>X121212121</GKV_Versichertennummer>
                                                </Versichertendaten_GKV>
                                                <Nachname>Musterfrau</Nachname>
                                                <Vornamen>Maxi</Vornamen>
                                                <Geschlecht>W</Geschlecht>
                                                <Geburtsdatum Datumsgenauigkeit="E">1960-01-16</Geburtsdatum>
                                                <Adresse>
                                                    <Strasse>Musterweg 11</Strasse>
                                                    <Land>DE</Land>
                                                    <PLZ>12345</PLZ>
                                                    <Ort>Musterstadt</Ort>
                                                </Adresse>
                                            </Patienten_Stammdaten>
                                            <Menge_Meldung>') || to_clob('
                                                <Meldung Meldung_ID="0003C812-12345/12345" Melder_ID="12345/12345">
                                                    <Meldebegruendung>I</Meldebegruendung>
                                                    <Eigene_Leistung>J</Eigene_Leistung>
                                                    <Tumorzuordnung Tumor_ID="1">
                                                        <Primaertumor_ICD>
                                                            <Code>C43.5</Code>
                                                            <Version>10 2023 GM</Version>
                                                        </Primaertumor_ICD>
                                                        <Diagnosedatum Datumsgenauigkeit="E">2023-10-11</Diagnosedatum>
                                                        <Seitenlokalisation>T</Seitenlokalisation>
                                                        <Morphologie_ICD_O>
                                                            <Code>8720/3</Code>
                                                            <Version>33</Version>
                                                        </Morphologie_ICD_O>
                                                    </Tumorzuordnung>
                                                    <SYST SYST_ID="4444444">
                                                        <Meldeanlass>behandlungsbeginn</Meldeanlass>
                                                        <Intention>P</Intention>
                                                        <Stellung_OP>O</Stellung_OP>
                                                        <Therapieart>SO</Therapieart>
                                                        <Protokoll>BSC: Best Supportive Care</Protokoll>
                                                        <Beginn Datumsgenauigkeit="E">2024-03-22</Beginn>
                                                        <Nebenwirkungen>
                                                            <Grad_maximal2_oder_unbekannt>K</Grad_maximal2_oder_unbekannt>
                                                        </Nebenwirkungen>
                                                    </SYST>
                                                </Meldung>
                                            </Menge_Meldung>') || to_clob('
                                        </Patient>
                                    </Menge_Patient>
                                    <Menge_Melder>
                                        <Melder ID="12345/12345">
                                            <KH_Abt_Station_Praxis>CCC</KH_Abt_Station_Praxis>
                                            <Anschrift>Musterstrasse 1</Anschrift>
                                            <PLZ>12345</PLZ>
                                            <Ort>Musterstadt</Ort>
                                        </Melder>
                                    </Menge_Melder>
                                </oBDS>
'), 1, '1055555777');


INSERT INTO DWH_ROUTINE.STG_ONKOSTAR_LKR_MELDUNG_EXPORT (ID, LKR_MELDUNG, LKR_EXPORT, TYP, XML_DATEN, VERSIONSNUMMER,
                                                         REFERENZ_NUMMER)
VALUES (9, 9, 1, 1, to_clob('<?xml version="1.0" encoding="UTF-8"?><oBDS xmlns="http://www.basisdatensatz.de/oBDS/XML" Schema_Version="3.0.2">
                                    <Absender Absender_ID="12345/12345" Software_ID="ONKOSTAR" Software_Version="2.13.0" Installations_ID="2">
                                        <Bezeichnung>CCC</Bezeichnung>
                                        <Ansprechpartner>Dr. Absender</Ansprechpartner>
                                        <Anschrift>musterstrasse 1, 123456 Musterstadt</Anschrift>
                                    </Absender>
                                    <Meldedatum>2024-11-30</Meldedatum>
                                    <Menge_Patient>
                                        <Patient Patient_ID="9876543">
                                            <Patienten_Stammdaten>
                                                <Versichertendaten_GKV>
                                                    <IKNR>222333444</IKNR>
                                                    <GKV_Versichertennummer>X121212121</GKV_Versichertennummer>
                                                </Versichertendaten_GKV>
                                                <Nachname>Musterfrau</Nachname>
                                                <Vornamen>Maxi</Vornamen>
                                                <Geschlecht>W</Geschlecht>
                                                <Geburtsdatum Datumsgenauigkeit="E">1960-01-16</Geburtsdatum>
                                                <Adresse>
                                                    <Strasse>Musterweg 11</Strasse>
                                                    <Land>DE</Land>
                                                    <PLZ>12345</PLZ>
                                                    <Ort>Musterstadt</Ort>
                                                </Adresse>
                                            </Patienten_Stammdaten>
                                            <Menge_Meldung>') || to_clob('
                                                <Meldung Meldung_ID="0004F608-12345/12345" Melder_ID="12345/12345">
                                                    <Meldebegruendung>I</Meldebegruendung>
                                                    <Eigene_Leistung>J</Eigene_Leistung>
                                                    <Tumorzuordnung Tumor_ID="1">
                                                        <Primaertumor_ICD>
                                                            <Code>C43.5</Code>
                                                            <Version>10 2023 GM</Version>
                                                        </Primaertumor_ICD>
                                                        <Diagnosedatum Datumsgenauigkeit="E">2023-10-20</Diagnosedatum>
                                                        <Seitenlokalisation>T</Seitenlokalisation>
                                                        <Morphologie_ICD_O>
                                                            <Code>8720/3</Code>
                                                            <Version>33</Version>
                                                        </Morphologie_ICD_O>
                                                    </Tumorzuordnung>
                                                    <SYST SYST_ID="4444444">
                                                        <Meldeanlass>behandlungsende</Meldeanlass>
                                                        <Intention>P</Intention>
                                                        <Stellung_OP>O</Stellung_OP>
                                                        <Therapieart>SO</Therapieart>
                                                        <Protokoll>BSC: Best Supportive Care</Protokoll>
                                                        <Beginn Datumsgenauigkeit="E">2024-03-22</Beginn>
                                                        <Ende_Grund>E</Ende_Grund>
                                                        <Ende>2024-03-22</Ende>
                                                        <Nebenwirkungen>
                                                            <Grad_maximal2_oder_unbekannt>K</Grad_maximal2_oder_unbekannt>
                                                        </Nebenwirkungen>
                                                    </SYST>
                                                </Meldung>
                                            </Menge_Meldung>') || to_clob('
                                        </Patient>
                                    </Menge_Patient>
                                    <Menge_Melder>
                                        <Melder ID="12345/12345">
                                            <KH_Abt_Station_Praxis>CCC</KH_Abt_Station_Praxis>
                                            <Anschrift>Musterstrasse 1</Anschrift>
                                            <PLZ>12345</PLZ>
                                            <Ort>Musterstadt</Ort>
                                        </Melder>
                                    </Menge_Melder>
                                </oBDS>
'), 1, '1055555999');

INSERT INTO DWH_ROUTINE.STG_ONKOSTAR_LKR_MELDUNG_EXPORT (ID, LKR_MELDUNG, LKR_EXPORT, TYP, XML_DATEN, VERSIONSNUMMER,
                                                         REFERENZ_NUMMER)
VALUES (10, 10, 1, 1, to_clob('<?xml version="1.0" encoding="UTF-8"?><oBDS xmlns="http://www.basisdatensatz.de/oBDS/XML" Schema_Version="3.0.2">
                                    <Absender Absender_ID="12345/12345" Software_ID="ONKOSTAR" Software_Version="2.13.0" Installations_ID="2">
                                        <Bezeichnung>CCC</Bezeichnung>
                                        <Ansprechpartner>Dr. Absender</Ansprechpartner>
                                        <Anschrift>musterstrasse 1, 123456 Musterstadt</Anschrift>
                                    </Absender>
                                    <Meldedatum>2024-11-30</Meldedatum>
                                    <Menge_Patient>
                                        <Patient Patient_ID="9876543">
                                            <Patienten_Stammdaten>
                                                <Versichertendaten_GKV>
                                                    <IKNR>222333444</IKNR>
                                                    <GKV_Versichertennummer>X121212121</GKV_Versichertennummer>
                                                </Versichertendaten_GKV>
                                                <Nachname>Musterfrau</Nachname>
                                                <Vornamen>Maxi</Vornamen>
                                                <Geschlecht>W</Geschlecht>
                                                <Geburtsdatum Datumsgenauigkeit="E">1960-01-16</Geburtsdatum>
                                                <Adresse>
                                                    <Strasse>Musterweg 11</Strasse>
                                                    <Land>DE</Land>
                                                    <PLZ>12345</PLZ>
                                                    <Ort>Musterstadt</Ort>
                                                </Adresse>
                                            </Patienten_Stammdaten>
                                            <Menge_Meldung>') || to_clob('
                                                <Meldung Meldung_ID="0003F310-555555/12345" Melder_ID="555555/12345">
                                                    <Meldebegruendung>I</Meldebegruendung>
                                                    <Eigene_Leistung>J</Eigene_Leistung>
                                                    <Tumorzuordnung Tumor_ID="1">
                                                        <Primaertumor_ICD>
                                                            <Code>C43.5</Code>
                                                            <Version>10 2023 GM</Version>
                                                        </Primaertumor_ICD>
                                                        <Diagnosedatum Datumsgenauigkeit="E">2023-10-20</Diagnosedatum>
                                                        <Seitenlokalisation>T</Seitenlokalisation>
                                                        <Morphologie_ICD_O>
                                                            <Code>8720/3</Code>
                                                            <Version>33</Version>
                                                        </Morphologie_ICD_O>
                                                    </Tumorzuordnung>
                                                    <SYST SYST_ID="5555555">
                                                        <Meldeanlass>behandlungsende</Meldeanlass>
                                                        <Intention>K</Intention>
                                                        <Stellung_OP>A</Stellung_OP>
                                                        <Therapieart>CI</Therapieart>
                                                        <Beginn Datumsgenauigkeit="E">2024-02-12</Beginn>
                                                        <Menge_Substanz>
                                                            <Substanz>
                                                                <Bezeichnung>Ipilimumab</Bezeichnung>
                                                            </Substanz>
                                                        </Menge_Substanz>
                                                        <Ende_Grund>A</Ende_Grund>
                                                        <Ende>2024-02-18</Ende>
                                                        <Nebenwirkungen>
                                                            <Grad_maximal2_oder_unbekannt>U</Grad_maximal2_oder_unbekannt>
                                                        </Nebenwirkungen>
                                                        <Modul_Allgemein>
                                                            <Sozialdienstkontakt>
                                                                <Datum>2024-01-21</Datum>
                                                            </Sozialdienstkontakt>
                                                            <Studienteilnahme>
                                                                <NU>N</NU>
                                                            </Studienteilnahme>
                                                        </Modul_Allgemein>
                                                    </SYST>
                                                </Meldung>
                                            </Menge_Meldung>') || to_clob('
                                        </Patient>
                                    </Menge_Patient>
                                    <Menge_Melder>
                                        <Melder ID="555555/12345">
                                            <KH_Abt_Station_Praxis>CCC</KH_Abt_Station_Praxis>
                                            <Anschrift>Musterstrasse 1</Anschrift>
                                            <PLZ>12345</PLZ>
                                            <Ort>Musterstadt</Ort>
                                        </Melder>
                                    </Menge_Melder>
                                </oBDS>
'), 2, '1055555888');
