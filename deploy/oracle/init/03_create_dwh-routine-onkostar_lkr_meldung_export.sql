ALTER SESSION SET CONTAINER = XEPDB1;
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
VALUES (1, 720, 1, 1, to_clob('<?xml version="1.0" encoding="UTF-8"?>
<ADT_GEKID xmlns="http://www.gekid.de/namespace" Schema_Version="2.2.1">
    <Absender Absender_ID="UKER_ONKOSTAR" Software_ID="ONKOSTAR" Installations_ID="2.9.6">
        <Absender_Bezeichnung>UKER</Absender_Bezeichnung>
        <Absender_Ansprechpartner/>
        <Absender_Anschrift>Maximiliansplatz 2, 91054 Erlangen</Absender_Anschrift>
    </Absender>
    <Menge_Patient>
        <Patient>
            <Patienten_Stammdaten Patient_ID="1055555999">
                <KrankenversichertenNr>Q00000000</KrankenversichertenNr>
                <KrankenkassenNr>10000000</KrankenkassenNr>
                <Patienten_Nachname>Doe</Patienten_Nachname>
                <Patienten_Vornamen>John</Patienten_Vornamen>
                <Patienten_Geschlecht>M</Patienten_Geschlecht>
                <Patienten_Geburtsdatum>11.09.1900</Patienten_Geburtsdatum>
                <Menge_Adresse>
                    <Adresse>
                        <Patienten_Strasse>Johnstr. 7</Patienten_Strasse>
                        <Patienten_Land>DE</Patienten_Land>
                        <Patienten_PLZ>91000</Patienten_PLZ>
                        <Patienten_Ort>Johncity</Patienten_Ort>
                    </Adresse>
                </Menge_Adresse>
            </Patienten_Stammdaten>
            <Menge_Meldung>
                <Meldung Meldung_ID="NC0000720" Melder_ID="NC">
                    <Meldedatum>20.05.2021</Meldedatum>
                    <Meldebegruendung>I</Meldebegruendung>
                    <Meldeanlass>diagnose</Meldeanlass>
                    <Tumorzuordnung Tumor_ID="1">
                        <Primaertumor_ICD_Code>C72.0</Primaertumor_ICD_Code>
                        <Primaertumor_ICD_Version>10 2021 GM</Primaertumor_ICD_Version>
                        <Diagnosedatum>18.03.2021</Diagnosedatum>
                        <Seitenlokalisation>T</Seitenlokalisation>
                    </Tumorzuordnung>
                    <Diagnose Tumor_ID="1">
                        <Primaertumor_ICD_Code>C72.0</Primaertumor_ICD_Code>
                        <Primaertumor_ICD_Version>10 2021 GM</Primaertumor_ICD_Version>
                        <Primaertumor_Topographie_ICD_O>C72.0</Primaertumor_Topographie_ICD_O>
                        <Primaertumor_Topographie_ICD_O_Version>32</Primaertumor_Topographie_ICD_O_Version>
                        <Diagnosedatum>18.03.2021</Diagnosedatum>
                        <Diagnosesicherung>7</Diagnosesicherung>
                        <Seitenlokalisation>T</Seitenlokalisation>') || to_clob(' <Menge_Fruehere_Tumorerkrankung>
                            <Fruehere_Tumorerkrankung>
                                <ICD_Code>C41.01</ICD_Code>
                                <ICD_Version>10 2021 GM</ICD_Version>
                                <Diagnosedatum>08.02.2021</Diagnosedatum>
                            </Fruehere_Tumorerkrankung>
                        </Menge_Fruehere_Tumorerkrankung>
                        <Menge_Histologie>
                            <Histologie Histologie_ID="8594">
                                <Tumor_Histologiedatum>18.03.2021</Tumor_Histologiedatum>
                                <Histologie_EinsendeNr>N 9391/3</Histologie_EinsendeNr>
                                <Morphologie_Code>9391/3</Morphologie_Code>
                                <Morphologie_ICD_O_Version>32</Morphologie_ICD_O_Version>
                                <Grading>T</Grading>
                            </Histologie>
                        </Menge_Histologie>
                        <cTNM TNM_ID="65571">
                            <TNM_Datum>21.10.2021</TNM_Datum>
                            <TNM_Version>8</TNM_Version>
                            <TNM_c_p_u_Praefix_T>c</TNM_c_p_u_Praefix_T>
                            <TNM_T>0</TNM_T>
                            <TNM_c_p_u_Praefix_N>c</TNM_c_p_u_Praefix_N>
                            <TNM_N>1</TNM_N>
                            <TNM_c_p_u_Praefix_M>c</TNM_c_p_u_Praefix_M>
                            <TNM_M>0</TNM_M>
                        </cTNM>
                        <pTNM TNM_ID="75685">
                            <TNM_Datum>05.11.2021</TNM_Datum>
                            <TNM_Version>8</TNM_Version>
                            <TNM_c_p_u_Praefix_N>p</TNM_c_p_u_Praefix_N>
                            <TNM_N>2a</TNM_N>
                            <TNM_L>L0</TNM_L>
                            <TNM_V>V0</TNM_V>
                            <TNM_Pn>Pn0</TNM_Pn>
                        </pTNM>
                        <Menge_FM>') || to_clob(' <Fernmetastase>
                              <FM_Diagnosedatum>26.08.2021</FM_Diagnosedatum>
                              <FM_Lokalisation>OSS</FM_Lokalisation>
                            </Fernmetastase>
                            <Fernmetastase>
                              <FM_Diagnosedatum>12.02.2021</FM_Diagnosedatum>
                              <FM_Lokalisation>HEP</FM_Lokalisation>
                            </Fernmetastase>
                            <Fernmetastase>
                              <FM_Diagnosedatum>12.02.2021</FM_Diagnosedatum>
                              <FM_Lokalisation>LYM</FM_Lokalisation>
                            </Fernmetastase>
                            <Fernmetastase>
                                <FM_Diagnosedatum>12.02.2021</FM_Diagnosedatum>
                                <FM_Lokalisation>OTH</FM_Lokalisation>
                            </Fernmetastase>
                            <Fernmetastase>
                                <FM_Diagnosedatum>12.02.2021</FM_Diagnosedatum>
                                <FM_Lokalisation>OTH</FM_Lokalisation>
                            </Fernmetastase>
                          </Menge_FM>
                        <Menge_Weitere_Klassifikation>
                            <Weitere_Klassifikation>
                                <Datum>18.03.2021</Datum>
                                <Name>WHO-2016</Name>
                                <Stadium>II</Stadium>
                            </Weitere_Klassifikation>
                        </Menge_Weitere_Klassifikation>
                    </Diagnose>
                </Meldung>
            </Menge_Meldung>
        </Patient>
    </Menge_Patient>
    <Menge_Melder>
        <Melder Melder_ID="NC">
            <Meldende_Stelle>NC</Meldende_Stelle>
        </Melder>
    </Menge_Melder>
</ADT_GEKID>'), 1, '1055555999');


INSERT INTO DWH_ROUTINE.STG_ONKOSTAR_LKR_MELDUNG_EXPORT (ID, LKR_MELDUNG, LKR_EXPORT, TYP, XML_DATEN, VERSIONSNUMMER,
                                                         REFERENZ_NUMMER)
VALUES (2, 723, 1, 1, to_clob('<?xml version="1.0" encoding="UTF-8"?>
<ADT_GEKID xmlns="http://www.gekid.de/namespace" Schema_Version="2.2.1">
    <Absender Absender_ID="UKER_ONKOSTAR" Software_ID="ONKOSTAR" Installations_ID="2.9.6">
        <Absender_Bezeichnung>UKER</Absender_Bezeichnung>
        <Absender_Ansprechpartner/>
        <Absender_Anschrift>Maximiliansplatz 2, 91054 Erlangen</Absender_Anschrift>
    </Absender>
    <Menge_Patient>
        <Patient>
            <Patienten_Stammdaten Patient_ID="1055555999">
                <KrankenversichertenNr>Q00000000</KrankenversichertenNr>
                <KrankenkassenNr>10000000</KrankenkassenNr>
                <Patienten_Nachname>Doe</Patienten_Nachname>
                <Patienten_Vornamen>John</Patienten_Vornamen>
                <Patienten_Geschlecht>M</Patienten_Geschlecht>
                <Patienten_Geburtsdatum>11.09.1900</Patienten_Geburtsdatum>
                <Menge_Adresse>
                    <Adresse>
                        <Patienten_Strasse>Johnstr. 7</Patienten_Strasse>
                        <Patienten_Land>DE</Patienten_Land>
                        <Patienten_PLZ>91000</Patienten_PLZ>
                        <Patienten_Ort>Johncity</Patienten_Ort>
                    </Adresse>
                </Menge_Adresse>
            </Patienten_Stammdaten>
            <Menge_Meldung>
                <Meldung Meldung_ID="KC0000723" Melder_ID="KC">
                    <Meldedatum>20.05.2021</Meldedatum>
                    <Meldebegruendung>I</Meldebegruendung>
                    <Meldeanlass>diagnose</Meldeanlass>
                    <Tumorzuordnung Tumor_ID="2">
                        <Primaertumor_ICD_Code>C41.01</Primaertumor_ICD_Code>
                        <Primaertumor_ICD_Version>10 2021 GM</Primaertumor_ICD_Version>
                        <Diagnosedatum>08.02.2021</Diagnosedatum>
                        <Seitenlokalisation>T</Seitenlokalisation>
                    </Tumorzuordnung>
                    <Diagnose Tumor_ID="2">
                        <Primaertumor_ICD_Code>C41.01</Primaertumor_ICD_Code>
                        <Primaertumor_ICD_Version>10 2021 GM</Primaertumor_ICD_Version>
                        <Primaertumor_Topographie_ICD_O>C41.02</Primaertumor_Topographie_ICD_O>
                        <Primaertumor_Topographie_ICD_O_Version>32</Primaertumor_Topographie_ICD_O_Version>
                        <Primaertumor_Topographie_ICD_O_Freitext>Mastikatorloge</Primaertumor_Topographie_ICD_O_Freitext>
                        <Diagnosedatum>08.02.2021</Diagnosedatum>
                        <Diagnosesicherung>7</Diagnosesicherung>
                        <Seitenlokalisation>T</Seitenlokalisation>
                        <Menge_Histologie>
                            <Histologie Histologie_ID="8611">
                                <Tumor_Histologiedatum>08.02.2021</Tumor_Histologiedatum>
                                <Histologie_EinsendeNr>12105169</Histologie_EinsendeNr>
                                <Morphologie_Code>9111/3</Morphologie_Code>
                                <Morphologie_ICD_O_Version>32</Morphologie_ICD_O_Version>
                                <Grading>U</Grading>
                            </Histologie>') || to_clob(' <Histologie Histologie_ID="8617">
                                <Tumor_Histologiedatum>25.02.2021</Tumor_Histologiedatum>
                                <Histologie_EinsendeNr>12105169</Histologie_EinsendeNr>
                                <Morphologie_Code>9222/3</Morphologie_Code>
                                <Morphologie_ICD_O_Version>32</Morphologie_ICD_O_Version>
                                <Grading>T</Grading>
                            </Histologie>
                        </Menge_Histologie>
                    </Diagnose>
                </Meldung>
            </Menge_Meldung>
        </Patient>
    </Menge_Patient>
    <Menge_Melder>
        <Melder Melder_ID="KC">
            <Meldende_Stelle>KC</Meldende_Stelle>
        </Melder>
    </Menge_Melder>
</ADT_GEKID>'), 1, '1055555999');

INSERT INTO DWH_ROUTINE.STG_ONKOSTAR_LKR_MELDUNG_EXPORT (ID, LKR_MELDUNG, LKR_EXPORT, TYP, XML_DATEN, VERSIONSNUMMER,
                                                         REFERENZ_NUMMER)
VALUES (3, 61, 1, 1, to_clob('<?xml version="1.0" encoding="UTF-8"?>
<ADT_GEKID xmlns="http://www.gekid.de/namespace" Schema_Version="2.1.2">
    <Absender Absender_ID="UKER" Software_ID="ONKOSTAR" Installations_ID="2.9.2">
        <Absender_Bezeichnung>UKER</Absender_Bezeichnung>
        <Absender_Ansprechpartner/>
        <Absender_Anschrift>Maximiliansplatz 2, 91054 Erlangen</Absender_Anschrift>
    </Absender>
    <Menge_Patient>
        <Patient>
            <Patienten_Stammdaten Patient_ID="1055555999">
                <KrankenversichertenNr>Q00000000</KrankenversichertenNr>
                <KrankenkassenNr>10000000</KrankenkassenNr>
                <Patienten_Nachname>Doe</Patienten_Nachname>
                <Patienten_Vornamen>John</Patienten_Vornamen>
                <Patienten_Geschlecht>M</Patienten_Geschlecht>
                <Patienten_Geburtsdatum>11.09.1900</Patienten_Geburtsdatum>
                <Menge_Adresse>
                    <Adresse>
                        <Patienten_Strasse>Johnstr. 7</Patienten_Strasse>
                        <Patienten_Land>DE</Patienten_Land>
                        <Patienten_PLZ>91000</Patienten_PLZ>
                        <Patienten_Ort>Johncity</Patienten_Ort>
                    </Adresse>
                </Menge_Adresse>
            </Patienten_Stammdaten>
            <Menge_Meldung>
                <Meldung Meldung_ID="NC0000061" Melder_ID="NC">
                    <Meldedatum>06.05.2021</Meldedatum>
                    <Meldebegruendung>I</Meldebegruendung>
                    <Meldeanlass>behandlungsende</Meldeanlass>
                    <Tumorzuordnung Tumor_ID="1">
                        <Primaertumor_ICD_Code>C72.0</Primaertumor_ICD_Code>
                        <Primaertumor_ICD_Version>10 2021 GM</Primaertumor_ICD_Version>
                        <Diagnosedatum>18.03.2021</Diagnosedatum>
                        <Seitenlokalisation>T</Seitenlokalisation>
                    </Tumorzuordnung>
                    <Menge_OP>
                        <OP OP_ID="741">
                            <OP_Intention>K</OP_Intention>
                            <OP_Datum>04.01.2021</OP_Datum>
                            <Menge_OPS>
                                <OP_OPS>5-015.4</OP_OPS>
                                <OP_OPS>5-852.g0</OP_OPS>
                                <OP_OPS>5-930.00</OP_OPS>
                                <OP_OPS>5-930.4</OP_OPS>
                                <OP_OPS>5-021.0</OP_OPS>
                                <OP_OPS>5-984</OP_OPS>
                                <OP_OPS>5-010.01</OP_OPS>
                                <OP_OPS>5-988.0</OP_OPS>
                            </Menge_OPS>') || to_clob('<OP_OPS_Version>2021</OP_OPS_Version>
                            <Histologie Histologie_ID="8594">
                                <Tumor_Histologiedatum>20.03.2021</Tumor_Histologiedatum>
                                <Histologie_EinsendeNr>N 9391/8</Histologie_EinsendeNr>
                                <Morphologie_Code>9391/8</Morphologie_Code>
                                <Morphologie_ICD_O_Version>32</Morphologie_ICD_O_Version>
                                <Grading>U</Grading>
                            </Histologie>
                            <TNM TNM_ID="75685">
                                <TNM_Datum>17.03.2021</TNM_Datum>
                                <TNM_Version>8</TNM_Version>
                                <TNM_c_p_u_Praefix_T>p</TNM_c_p_u_Praefix_T>
                                <TNM_T>is</TNM_T>
                                <TNM_c_p_u_Praefix_N>c</TNM_c_p_u_Praefix_N>
                                <TNM_N>0</TNM_N>
                                <TNM_c_p_u_Praefix_M>c</TNM_c_p_u_Praefix_M>
                                <TNM_M>0</TNM_M>
                                <TNM_L>L0</TNM_L>
                                <TNM_V>V0</TNM_V>
                                <TNM_Pn>Pn0</TNM_Pn>
                            </TNM>
                            <Residualstatus>
                                <Lokale_Beurteilung_Residualstatus>R0</Lokale_Beurteilung_Residualstatus>
                                <Gesamtbeurteilung_Residualstatus>R0</Gesamtbeurteilung_Residualstatus>
                            </Residualstatus>
                            <Menge_Komplikation>
                                <OP_Komplikation>N</OP_Komplikation>
                            </Menge_Komplikation>
                            <Menge_Operateur>
                                <Name_Operateur>Darwin, Charles</Name_Operateur>
                            </Menge_Operateur>
                        </OP>
                    </Menge_OP>
                </Meldung>
            </Menge_Meldung>
        </Patient>
    </Menge_Patient>
    <Menge_Melder>
        <Melder Melder_ID="NC">
            <Meldende_Stelle>NC</Meldende_Stelle>
        </Melder>
    </Menge_Melder>
</ADT_GEKID>'), 1, '1055555999');

INSERT INTO DWH_ROUTINE.STG_ONKOSTAR_LKR_MELDUNG_EXPORT (ID, LKR_MELDUNG, LKR_EXPORT, TYP, XML_DATEN, VERSIONSNUMMER,
                                                         REFERENZ_NUMMER)
VALUES (4, 4776, 1, 1, to_clob('<?xml version="1.0" encoding="UTF-8"?>
<ADT_GEKID xmlns="http://www.gekid.de/namespace" Schema_Version="2.2.1">
	<Absender Absender_ID="999999" Software_ID="ONKOSTAR" Installations_ID="2.9.8">
		<Absender_Bezeichnung>UKER</Absender_Bezeichnung>
		<Absender_Ansprechpartner/>
		<Absender_Anschrift>Maximiliansplatz 2, 91054 Erlangen</Absender_Anschrift>
	</Absender>
	<Menge_Patient>
		<Patient>
            <Patienten_Stammdaten Patient_ID="1055555999">
                <KrankenversichertenNr>Q00000000</KrankenversichertenNr>
                <KrankenkassenNr>10000000</KrankenkassenNr>
                <Patienten_Nachname>Doe</Patienten_Nachname>
                <Patienten_Vornamen>John</Patienten_Vornamen>
                <Patienten_Geschlecht>M</Patienten_Geschlecht>
                <Patienten_Geburtsdatum>11.09.1900</Patienten_Geburtsdatum>
                <Menge_Adresse>
                    <Adresse>
                        <Patienten_Strasse>Johnstr. 7</Patienten_Strasse>
                        <Patienten_Land>DE</Patienten_Land>
                        <Patienten_PLZ>91000</Patienten_PLZ>
                        <Patienten_Ort>Johncity</Patienten_Ort>
                    </Adresse>
                </Menge_Adresse>
            </Patienten_Stammdaten>
			<Menge_Meldung>
				<Meldung Meldung_ID="ST0004776" Melder_ID="ST">
					<Meldedatum>10.11.2021</Meldedatum>
					<Meldebegruendung>I</Meldebegruendung>
					<Meldeanlass>behandlungsende</Meldeanlass>
					<Tumorzuordnung Tumor_ID="2">
                        <Primaertumor_ICD_Code>C41.01</Primaertumor_ICD_Code>
                        <Primaertumor_ICD_Version>10 2021 GM</Primaertumor_ICD_Version>
                        <Diagnosedatum>08.02.2021</Diagnosedatum>
                        <Seitenlokalisation>T</Seitenlokalisation>
                    </Tumorzuordnung>
					<Menge_ST>') || to_clob('<ST ST_ID="45968">
							<ST_Intention>K</ST_Intention>
							<ST_Stellung_OP>S</ST_Stellung_OP>
							<Menge_Bestrahlung>
								<Bestrahlung>
									<ST_Zielgebiet>5.4.-</ST_Zielgebiet>
									<ST_Seite_Zielgebiet>U</ST_Seite_Zielgebiet>
									<ST_Beginn_Datum>13.09.2021</ST_Beginn_Datum>
									<ST_Ende_Datum>14.09.2021</ST_Ende_Datum>
								</Bestrahlung>
								<Bestrahlung>
									<ST_Zielgebiet>1.2</ST_Zielgebiet>
									<ST_Seite_Zielgebiet>L</ST_Seite_Zielgebiet>
									<ST_Beginn_Datum>01.10.2021</ST_Beginn_Datum>
									<ST_Ende_Datum>02.10.2021</ST_Ende_Datum>
								</Bestrahlung>
							</Menge_Bestrahlung>
							<ST_Ende_Grund>E</ST_Ende_Grund>
							<Menge_Nebenwirkung>
								<ST_Nebenwirkung>
									<Nebenwirkung_Grad>K</Nebenwirkung_Grad>
								</ST_Nebenwirkung>
							</Menge_Nebenwirkung>
						</ST>
					</Menge_ST>
				</Meldung>
			</Menge_Meldung>
		</Patient>
	</Menge_Patient>
	<Menge_Melder>
		<Melder Melder_ID="ST">
			<Meldende_Stelle>ST</Meldende_Stelle>
		</Melder>
	</Menge_Melder>
</ADT_GEKID>'), 1, '1055555999');

INSERT INTO DWH_ROUTINE.STG_ONKOSTAR_LKR_MELDUNG_EXPORT (ID, LKR_MELDUNG, LKR_EXPORT, TYP, XML_DATEN, VERSIONSNUMMER,
                                                         REFERENZ_NUMMER)
VALUES (5, 788, 1, 1, to_clob('<?xml version="1.0" encoding="UTF-8"?>
<ADT_GEKID xmlns="http://www.gekid.de/namespace" Schema_Version="2.2.1">
    <Absender Absender_ID="UKER_ONKOSTAR" Software_ID="ONKOSTAR" Installations_ID="2.9.6">
        <Absender_Bezeichnung>UKER</Absender_Bezeichnung>
        <Absender_Ansprechpartner/>
        <Absender_Anschrift>Maximiliansplatz 2, 91054 Erlangen</Absender_Anschrift>
    </Absender>
    <Menge_Patient>
        <Patient>
            <Patienten_Stammdaten Patient_ID="1055555888">
                <KrankenversichertenNr>Q00000000</KrankenversichertenNr>
                <KrankenkassenNr>10000000</KrankenkassenNr>
                <Patienten_Nachname>Doe</Patienten_Nachname>
                <Patienten_Vornamen>John</Patienten_Vornamen>
                <Patienten_Geschlecht>M</Patienten_Geschlecht>
                <Patienten_Geburtsdatum>11.09.1900</Patienten_Geburtsdatum>
                <Menge_Adresse>
                    <Adresse>
                        <Patienten_Strasse>Johnstr. 7</Patienten_Strasse>
                        <Patienten_Land>DE</Patienten_Land>
                        <Patienten_PLZ>91000</Patienten_PLZ>
                        <Patienten_Ort>Johncity</Patienten_Ort>
                    </Adresse>
                </Menge_Adresse>
            </Patienten_Stammdaten>
            <Menge_Meldung>
                <Meldung Meldung_ID="NC0000788" Melder_ID="NC">
                    <Meldedatum>20.05.2021</Meldedatum>
                    <Meldebegruendung>I</Meldebegruendung>
                    <Meldeanlass>diagnose</Meldeanlass>
                    <Tumorzuordnung Tumor_ID="1">
                        <Primaertumor_ICD_Code>C72.0</Primaertumor_ICD_Code>
                        <Primaertumor_ICD_Version>10 2021 GM</Primaertumor_ICD_Version>
                        <Diagnosedatum>18.05.2021</Diagnosedatum>
                        <Seitenlokalisation>R</Seitenlokalisation>
                    </Tumorzuordnung>') || to_clob('<Diagnose Tumor_ID="1">
                        <Primaertumor_ICD_Code>C72.0</Primaertumor_ICD_Code>
                        <Primaertumor_ICD_Version>10 2021 GM</Primaertumor_ICD_Version>
                        <Primaertumor_Topographie_ICD_O>C72.0</Primaertumor_Topographie_ICD_O>
                        <Primaertumor_Topographie_ICD_O_Version>32</Primaertumor_Topographie_ICD_O_Version>
                        <Diagnosedatum>18.05.2021</Diagnosedatum>
                        <Diagnosesicherung>7</Diagnosesicherung>
                        <Seitenlokalisation>R</Seitenlokalisation>
                        <Menge_Fruehere_Tumorerkrankung>
                            <Fruehere_Tumorerkrankung>
                                <ICD_Code>C41.01</ICD_Code>
                                <ICD_Version>10 2021 GM</ICD_Version>
                                <Diagnosedatum>08.02.2021</Diagnosedatum>
                            </Fruehere_Tumorerkrankung>
                        </Menge_Fruehere_Tumorerkrankung>
                        <Menge_Histologie>
                            <Histologie Histologie_ID="8588">
                                <Tumor_Histologiedatum>18.05.2021</Tumor_Histologiedatum>
                                <Histologie_EinsendeNr>N 9391/3</Histologie_EinsendeNr>
                                <Morphologie_Code>9391/5</Morphologie_Code>
                                <Morphologie_ICD_O_Version>32</Morphologie_ICD_O_Version>
                                <Grading>U</Grading>
                            </Histologie>
                        </Menge_Histologie>
                        <Menge_Weitere_Klassifikation>
                            <Weitere_Klassifikation>
                                <Datum>18.05.2021</Datum>
                                <Name>WHO-2016</Name>
                                <Stadium>III</Stadium>
                            </Weitere_Klassifikation>
                        </Menge_Weitere_Klassifikation>
                    </Diagnose>
                </Meldung>
            </Menge_Meldung>
        </Patient>
    </Menge_Patient>
    <Menge_Melder>
        <Melder Melder_ID="NC">
            <Meldende_Stelle>NC</Meldende_Stelle>
        </Melder>
    </Menge_Melder>
</ADT_GEKID>'), 1, '1055555888');

INSERT INTO DWH_ROUTINE.STG_ONKOSTAR_LKR_MELDUNG_EXPORT (ID, LKR_MELDUNG, LKR_EXPORT, TYP, XML_DATEN, VERSIONSNUMMER,
                                                         REFERENZ_NUMMER)
VALUES (6, 5452, 1, 1, to_clob('<?xml version="1.0" encoding="UTF-8"?>
<ADT_GEKID xmlns="http://www.gekid.de/namespace" Schema_Version="2.2.1">
	<Absender Absender_ID="999999" Software_ID="ONKOSTAR" Installations_ID="2.9.8">
		<Absender_Bezeichnung>UKER</Absender_Bezeichnung>
		<Absender_Ansprechpartner/>
		<Absender_Anschrift>Maximiliansplatz 2, 91054 Erlangen</Absender_Anschrift>
	</Absender>
	<Menge_Patient>
		<Patient>
            <Patienten_Stammdaten Patient_ID="1055555888">
                <KrankenversichertenNr>Q00000000</KrankenversichertenNr>
                <KrankenkassenNr>10000000</KrankenkassenNr>
                <Patienten_Nachname>Doe</Patienten_Nachname>
                <Patienten_Vornamen>John</Patienten_Vornamen>
                <Patienten_Geschlecht>M</Patienten_Geschlecht>
                <Patienten_Geburtsdatum>11.09.1900</Patienten_Geburtsdatum>
                <Menge_Adresse>
                    <Adresse>
                        <Patienten_Strasse>Johnstr. 7</Patienten_Strasse>
                        <Patienten_Land>DE</Patienten_Land>
                        <Patienten_PLZ>91000</Patienten_PLZ>
                        <Patienten_Ort>Johncity</Patienten_Ort>
                    </Adresse>
                </Menge_Adresse>
            </Patienten_Stammdaten>
			<Menge_Meldung>
				<Meldung Meldung_ID="ST0005452" Melder_ID="ST">
					<Meldedatum>10.11.2021</Meldedatum>
					<Meldebegruendung>I</Meldebegruendung>
					<Meldeanlass>behandlungsbeginn</Meldeanlass>
					<Tumorzuordnung Tumor_ID="1">
                        <Primaertumor_ICD_Code>C72.0</Primaertumor_ICD_Code>
                        <Primaertumor_ICD_Version>10 2021 GM</Primaertumor_ICD_Version>
                        <Diagnosedatum>18.05.2021</Diagnosedatum>
                        <Seitenlokalisation>R</Seitenlokalisation>
                    </Tumorzuordnung>') || to_clob('<Menge_ST>
						<ST ST_ID="45900">
							<ST_Intention>K</ST_Intention>
							<ST_Stellung_OP>A</ST_Stellung_OP>
							<Menge_Bestrahlung>
								<Bestrahlung>
									<ST_Zielgebiet>5.4.-</ST_Zielgebiet>
									<ST_Seite_Zielgebiet>T</ST_Seite_Zielgebiet>
									<ST_Beginn_Datum>01.01.1900</ST_Beginn_Datum>
									<ST_Ende_Datum>02.01.1900</ST_Ende_Datum>
								</Bestrahlung>
								<Bestrahlung>
									<ST_Zielgebiet>1.2</ST_Zielgebiet>
									<ST_Seite_Zielgebiet>R</ST_Seite_Zielgebiet>
									<ST_Beginn_Datum>01.12.1800</ST_Beginn_Datum>
									<ST_Ende_Datum>02.12.1800</ST_Ende_Datum>
								</Bestrahlung>
							</Menge_Bestrahlung>
							<ST_Ende_Grund>L</ST_Ende_Grund>
							<Menge_Nebenwirkung>
								<ST_Nebenwirkung>
									<Nebenwirkung_Grad>4</Nebenwirkung_Grad>
								</ST_Nebenwirkung>
							</Menge_Nebenwirkung>
						</ST>
					</Menge_ST>
				</Meldung>
			</Menge_Meldung>
		</Patient>
	</Menge_Patient>
	<Menge_Melder>
		<Melder Melder_ID="ST">
			<Meldende_Stelle>ST</Meldende_Stelle>
		</Melder>
	</Menge_Melder>
</ADT_GEKID>'), 1, '1055555888');


INSERT INTO DWH_ROUTINE.STG_ONKOSTAR_LKR_MELDUNG_EXPORT (ID, LKR_MELDUNG, LKR_EXPORT, TYP, XML_DATEN, VERSIONSNUMMER,
                                                         REFERENZ_NUMMER)
VALUES (7, 1238, 1, 1, to_clob('<?xml version="1.0" encoding="UTF-8"?>
<ADT_GEKID xmlns="http://www.gekid.de/namespace" Schema_Version="2.2.1">
	<Absender Absender_ID="999999" Software_ID="ONKOSTAR" Installations_ID="2.9.8">
		<Absender_Bezeichnung>UKER</Absender_Bezeichnung>
		<Absender_Ansprechpartner/>
		<Absender_Anschrift>Maximiliansplatz 2, 91054 Erlangen</Absender_Anschrift>
	</Absender>
	<Menge_Patient>
		<Patient>
            <Patienten_Stammdaten Patient_ID="1055555888">
                <KrankenversichertenNr>Q00000000</KrankenversichertenNr>
                <KrankenkassenNr>10000000</KrankenkassenNr>
                <Patienten_Nachname>Doe</Patienten_Nachname>
                <Patienten_Vornamen>John</Patienten_Vornamen>
                <Patienten_Geschlecht>M</Patienten_Geschlecht>
                <Patienten_Geburtsdatum>11.09.1900</Patienten_Geburtsdatum>
                <Menge_Adresse>
                    <Adresse>
                        <Patienten_Strasse>Johnstr. 7</Patienten_Strasse>
                        <Patienten_Land>DE</Patienten_Land>
                        <Patienten_PLZ>91000</Patienten_PLZ>
                        <Patienten_Ort>Johncity</Patienten_Ort>
                    </Adresse>
                </Menge_Adresse>
            </Patienten_Stammdaten>
			<Menge_Meldung>
				<Meldung Meldung_ID="ST0001238" Melder_ID="ST">
					<Meldedatum>10.11.2021</Meldedatum>
					<Meldebegruendung>I</Meldebegruendung>
					<Meldeanlass>behandlungsende</Meldeanlass>
					<Tumorzuordnung Tumor_ID="1">
                        <Primaertumor_ICD_Code>C72.0</Primaertumor_ICD_Code>
                        <Primaertumor_ICD_Version>10 2021 GM</Primaertumor_ICD_Version>
                        <Diagnosedatum>18.05.2021</Diagnosedatum>
                        <Seitenlokalisation>R</Seitenlokalisation>
                    </Tumorzuordnung>
					<Menge_ST>
						<ST ST_ID="45900">
							<ST_Intention>D</ST_Intention>
							<ST_Stellung_OP>N</ST_Stellung_OP>') || to_clob('<Menge_Bestrahlung>
								<Bestrahlung>
									<ST_Zielgebiet>5.4.-</ST_Zielgebiet>
									<ST_Seite_Zielgebiet>T</ST_Seite_Zielgebiet>
								</Bestrahlung>
								<Bestrahlung>
									<ST_Zielgebiet>1.2</ST_Zielgebiet>
									<ST_Seite_Zielgebiet>R</ST_Seite_Zielgebiet>
									<ST_Ende_Datum>02.12.2021</ST_Ende_Datum>
								</Bestrahlung>
							</Menge_Bestrahlung>
							<ST_Ende_Grund>L</ST_Ende_Grund>
							<Menge_Nebenwirkung>
								<ST_Nebenwirkung>
									<Nebenwirkung_Grad>4</Nebenwirkung_Grad>
								</ST_Nebenwirkung>
							</Menge_Nebenwirkung>
						</ST>
					</Menge_ST>
				</Meldung>
			</Menge_Meldung>
		</Patient>
	</Menge_Patient>
	<Menge_Melder>
		<Melder Melder_ID="ST">
			<Meldende_Stelle>ST</Meldende_Stelle>
		</Melder>
	</Menge_Melder>
</ADT_GEKID>'), 1, '1055555888');


INSERT INTO DWH_ROUTINE.STG_ONKOSTAR_LKR_MELDUNG_EXPORT (ID, LKR_MELDUNG, LKR_EXPORT, TYP, XML_DATEN, VERSIONSNUMMER,
                                                         REFERENZ_NUMMER)
VALUES (8, 4073, 1, 1, to_clob('<?xml version="1.0" encoding="UTF-8"?>
<ADT_GEKID xmlns="http://www.gekid.de/namespace" Schema_Version="2.2.1">
	<Absender Absender_ID="UKER_ONKOSTAR" Software_ID="ONKOSTAR" Installations_ID="2.9.8">
		<Absender_Bezeichnung>UKER</Absender_Bezeichnung>
		<Absender_Ansprechpartner/>
		<Absender_Anschrift>Maximiliansplatz 2, 91054 Erlangen</Absender_Anschrift>
	</Absender>
	<Menge_Patient>
		<Patient>
            <Patienten_Stammdaten Patient_ID="1055555777">
                <KrankenversichertenNr>Q00000000</KrankenversichertenNr>
                <KrankenkassenNr>10000000</KrankenkassenNr>
                <Patienten_Nachname>Doe</Patienten_Nachname>
                <Patienten_Vornamen>John</Patienten_Vornamen>
                <Patienten_Geschlecht>M</Patienten_Geschlecht>
                <Patienten_Geburtsdatum>11.09.1900</Patienten_Geburtsdatum>
                <Menge_Adresse>
                    <Adresse>
                        <Patienten_Strasse>Johnstr. 7</Patienten_Strasse>
                        <Patienten_Land>DE</Patienten_Land>
                        <Patienten_PLZ>91000</Patienten_PLZ>
                        <Patienten_Ort>Johncity</Patienten_Ort>
                    </Adresse>
                </Menge_Adresse>
            </Patienten_Stammdaten>
			<Menge_Meldung>
				<Meldung Meldung_ID="M50004073" Melder_ID="M5">
					<Meldedatum>23.11.2021</Meldedatum>
					<Meldebegruendung>I</Meldebegruendung>
					<Meldeanlass>behandlungsende</Meldeanlass>
					<Tumorzuordnung Tumor_ID="1">
						<Primaertumor_ICD_Code>C91.00</Primaertumor_ICD_Code>
						<Primaertumor_ICD_Version>10 2021 GM</Primaertumor_ICD_Version>
						<Diagnosedatum>21.05.2021</Diagnosedatum>
						<Seitenlokalisation>T</Seitenlokalisation>
					</Tumorzuordnung>
					<Menge_SYST>
						<SYST SYST_ID="39458">
							<SYST_Intention>K</SYST_Intention>
							<SYST_Stellung_OP>O</SYST_Stellung_OP>') || to_clob('<Menge_Therapieart>
								<SYST_Therapieart>CH</SYST_Therapieart>
								<SYST_Therapieart>IM</SYST_Therapieart>
							</Menge_Therapieart>
							<SYST_Beginn_Datum>22.05.2021</SYST_Beginn_Datum>
							<Menge_Substanz>
								<SYST_Substanz>Methotrexat (MTX)</SYST_Substanz>
								<SYST_Substanz>Cyclophosphamid</SYST_Substanz>
								<SYST_Substanz>Cytarabin (AraC)</SYST_Substanz>
								<SYST_Substanz>Mercaptopurin (6-MP, Purinethol)</SYST_Substanz>
							</Menge_Substanz>
							<SYST_Ende_Grund>P</SYST_Ende_Grund>
							<SYST_Ende_Datum>20.07.2021</SYST_Ende_Datum>
							<Menge_Nebenwirkung>
								<SYST_Nebenwirkung>
									<Nebenwirkung_Grad>U</Nebenwirkung_Grad>
									<Nebenwirkung_Art>10055599</Nebenwirkung_Art>
									<Nebenwirkung_Version>4.03</Nebenwirkung_Version>
								</SYST_Nebenwirkung>
							</Menge_Nebenwirkung>
						</SYST>
					</Menge_SYST>
				</Meldung>
			</Menge_Meldung>
		</Patient>
	</Menge_Patient>
	<Menge_Melder>
		<Melder Melder_ID="M5">
			<Meldende_Stelle>M5</Meldende_Stelle>
		</Melder>
	</Menge_Melder>
</ADT_GEKID>'), 1, '1055555777');


INSERT INTO DWH_ROUTINE.STG_ONKOSTAR_LKR_MELDUNG_EXPORT (ID, LKR_MELDUNG, LKR_EXPORT, TYP, XML_DATEN, VERSIONSNUMMER,
                                                         REFERENZ_NUMMER)
VALUES (9, 8318, 1, 1, to_clob('<?xml version="1.0" encoding="UTF-8"?>
<ADT_GEKID xmlns="http://www.gekid.de/namespace" Schema_Version="2.1.2">
    <Absender Absender_ID="UKER" Software_ID="ONKOSTAR" Installations_ID="2.9.2">
        <Absender_Bezeichnung>UKER</Absender_Bezeichnung>
        <Absender_Ansprechpartner/>
        <Absender_Anschrift>Maximiliansplatz 2, 91054 Erlangen</Absender_Anschrift>
    </Absender>
    <Menge_Patient>
        <Patient>
            <Patienten_Stammdaten Patient_ID="1055555999">
                <KrankenversichertenNr>Q00000000</KrankenversichertenNr>
                <KrankenkassenNr>10000000</KrankenkassenNr>
                <Patienten_Nachname>Doe</Patienten_Nachname>
                <Patienten_Vornamen>John</Patienten_Vornamen>
                <Patienten_Geschlecht>M</Patienten_Geschlecht>
                <Patienten_Geburtsdatum>11.09.1900</Patienten_Geburtsdatum>
                <Menge_Adresse>
                    <Adresse>
                        <Patienten_Strasse>Johnstr. 7</Patienten_Strasse>
                        <Patienten_Land>DE</Patienten_Land>
                        <Patienten_PLZ>91000</Patienten_PLZ>
                        <Patienten_Ort>Johncity</Patienten_Ort>
                    </Adresse>
                </Menge_Adresse>
            </Patienten_Stammdaten>
            <Menge_Meldung>
                <Meldung Meldung_ID="NU0008318" Melder_ID="NU">
                    <Meldedatum>28.07.2022</Meldedatum>
                    <Meldebegruendung>I</Meldebegruendung>
                    <Meldeanlass>statusaenderung</Meldeanlass>
                    <Tumorzuordnung Tumor_ID="1">
                        <Primaertumor_ICD_Code>C72.0</Primaertumor_ICD_Code>
                        <Primaertumor_ICD_Version>10 2021 GM</Primaertumor_ICD_Version>
                        <Diagnosedatum>18.03.2021</Diagnosedatum>
                        <Seitenlokalisation>T</Seitenlokalisation>
                    </Tumorzuordnung>
                    <Menge_Verlauf>
                        <Verlauf Verlauf_ID="77728">
                            <Untersuchungsdatum_Verlauf>26.08.2021</Untersuchungsdatum_Verlauf>
                            <Gesamtbeurteilung_Tumorstatus>P</Gesamtbeurteilung_Tumorstatus>
                            <Verlauf_Lokaler_Tumorstatus>K</Verlauf_Lokaler_Tumorstatus>
                            <Verlauf_Tumorstatus_Lymphknoten>K</Verlauf_Tumorstatus_Lymphknoten>
                            <Verlauf_Tumorstatus_Fernmetastasen>R</Verlauf_Tumorstatus_Fernmetastasen>
                            <Menge_FM>
                                <Fernmetastase>
                                    <FM_Diagnosedatum>26.08.2021</FM_Diagnosedatum>
                                    <FM_Lokalisation>OSS</FM_Lokalisation>
                                </Fernmetastase>
                                <Fernmetastase>
                                    <FM_Diagnosedatum>23.02.2021</FM_Diagnosedatum>
                                    <FM_Lokalisation>LYM</FM_Lokalisation>
                                  </Fernmetastase>
                                  <Fernmetastase>
                                      <FM_Diagnosedatum>12.02.2021</FM_Diagnosedatum>
                                      <FM_Lokalisation>OTH</FM_Lokalisation>
                                  </Fernmetastase>
                            </Menge_FM>') || to_clob(' <Allgemeiner_Leistungszustand>U</Allgemeiner_Leistungszustand>
                            <Modul_Prostata>
                                <PSA>0.160</PSA>
                                <DatumPSA>2021-08-26+02:00</DatumPSA>
                            </Modul_Prostata>
                            <Modul_Allgemein>
                                <DatumStudienrekrutierung>
                                    <NU>N</NU>
                                </DatumStudienrekrutierung>
                            </Modul_Allgemein>
                        </Verlauf>
                    </Menge_Verlauf>
                </Meldung>
            </Menge_Meldung>
        </Patient>
    </Menge_Patient>
    <Menge_Melder>
        <Melder Melder_ID="NU">
            <Meldende_Stelle>NU</Meldende_Stelle>
        </Melder>
    </Menge_Melder>
</ADT_GEKID>'), 1, '1055555999');

INSERT INTO DWH_ROUTINE.STG_ONKOSTAR_LKR_MELDUNG_EXPORT (ID, LKR_MELDUNG, LKR_EXPORT, TYP, XML_DATEN, VERSIONSNUMMER,
                                                         REFERENZ_NUMMER)
VALUES (10, 788, 1, 1, to_clob('<?xml version="1.0" encoding="UTF-8"?>
<ADT_GEKID xmlns="http://www.gekid.de/namespace" Schema_Version="2.2.1">
    <Absender Absender_ID="UKER_ONKOSTAR" Software_ID="ONKOSTAR" Installations_ID="2.9.6">
        <Absender_Bezeichnung>UKER</Absender_Bezeichnung>
        <Absender_Ansprechpartner/>
        <Absender_Anschrift>Maximiliansplatz 2, 91054 Erlangen</Absender_Anschrift>
    </Absender>
    <Menge_Patient>
        <Patient>
            <Patienten_Stammdaten Patient_ID="1055555888">
                <KrankenversichertenNr>Q00000000</KrankenversichertenNr>
                <KrankenkassenNr>10000000</KrankenkassenNr>
                <Patienten_Nachname>Doe</Patienten_Nachname>
                <Patienten_Vornamen>John</Patienten_Vornamen>
                <Patienten_Geschlecht>M</Patienten_Geschlecht>
                <Patienten_Geburtsdatum>11.09.1900</Patienten_Geburtsdatum>
                <Menge_Adresse>
                    <Adresse>
                        <Patienten_Strasse>Johnstr. 7</Patienten_Strasse>
                        <Patienten_Land>DE</Patienten_Land>
                        <Patienten_PLZ>91000</Patienten_PLZ>
                        <Patienten_Ort>Johncity</Patienten_Ort>
                    </Adresse>
                </Menge_Adresse>
            </Patienten_Stammdaten>
            <Menge_Meldung>
                <Meldung Meldung_ID="NC0000788" Melder_ID="NC">
                    <Meldedatum>20.05.2021</Meldedatum>
                    <Meldebegruendung>I</Meldebegruendung>
                    <Meldeanlass>diagnose</Meldeanlass>
                    <Tumorzuordnung Tumor_ID="1">
                        <Primaertumor_ICD_Code>C72.0</Primaertumor_ICD_Code>
                        <Primaertumor_ICD_Version>10 2021 GM</Primaertumor_ICD_Version>
                        <Diagnosedatum>18.05.2021</Diagnosedatum>
                        <Seitenlokalisation>R</Seitenlokalisation>
                    </Tumorzuordnung>') || to_clob('<Diagnose Tumor_ID="1">
                        <Primaertumor_ICD_Code>C72.0</Primaertumor_ICD_Code>
                        <Primaertumor_ICD_Version>10 2021 GM</Primaertumor_ICD_Version>
                        <Primaertumor_Topographie_ICD_O>C72.0</Primaertumor_Topographie_ICD_O>
                        <Primaertumor_Topographie_ICD_O_Version>32</Primaertumor_Topographie_ICD_O_Version>
                        <Diagnosedatum>30.10.2022</Diagnosedatum>
                        <Diagnosesicherung>7</Diagnosesicherung>
                        <Seitenlokalisation>R</Seitenlokalisation>
                        <Menge_Fruehere_Tumorerkrankung>
                            <Fruehere_Tumorerkrankung>
                                <ICD_Code>C41.01</ICD_Code>
                                <ICD_Version>10 2021 GM</ICD_Version>
                                <Diagnosedatum>08.02.2021</Diagnosedatum>
                            </Fruehere_Tumorerkrankung>
                        </Menge_Fruehere_Tumorerkrankung>
                        <Menge_Histologie>
                            <Histologie Histologie_ID="8588">
                                <Tumor_Histologiedatum>29.10.2022</Tumor_Histologiedatum>
                                <Histologie_EinsendeNr>N 9391/3</Histologie_EinsendeNr>
                                <Morphologie_Code>9391/5</Morphologie_Code>
                                <Morphologie_ICD_O_Version>32</Morphologie_ICD_O_Version>
                                <Grading>U</Grading>
                            </Histologie>
                        </Menge_Histologie>
                        <Menge_Weitere_Klassifikation>
                            <Weitere_Klassifikation>
                                <Datum>18.05.2021</Datum>
                                <Name>WHO-2016</Name>
                                <Stadium>III</Stadium>
                            </Weitere_Klassifikation>
                        </Menge_Weitere_Klassifikation>
                    </Diagnose>
                </Meldung>
            </Menge_Meldung>
        </Patient>
    </Menge_Patient>
    <Menge_Melder>
        <Melder Melder_ID="NC">
            <Meldende_Stelle>NC</Meldende_Stelle>
        </Melder>
    </Menge_Melder>
</ADT_GEKID>'), 2, '1055555888');
