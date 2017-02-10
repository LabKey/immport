DROP TABLE IF EXISTS lk_analyte;

CREATE TABLE lk_analyte
(
  
  analyte_accession VARCHAR(15) NOT NULL
    COMMENT "this is the analyte_acc_num field.",
  
  analyte_preferred VARCHAR(100) CHARACTER SET utf8 COLLATE utf8_bin NOT NULL
    COMMENT "this is the analyte_preferred field.",
  
  cluster_subunit_gene_ids VARCHAR(1000)
    COMMENT "this is the cluster_subunit_gene_ids field.",
  
  cluster_subunit_gene_symbols VARCHAR(1000)
    COMMENT "this is the cluster_subunit_gene_symbols field.",
  
  cluster_subunit_uniprot_ids VARCHAR(1000)
    COMMENT "this is the cluster_subunit_uniprot_ids field.",
  
  cluster_subunit_uniprot_names TEXT
    COMMENT "this is the cluster_subunit_uniprot_names field.",
  
  gene_additional_names TEXT 
    COMMENT "this is the gene_additional_names field.",
  
  gene_aliases TEXT
    COMMENT "this is the gene_aliases field.",
  
  gene_id VARCHAR(10)
    COMMENT "this is the gene_id field.",
  
  genetic_nomenclature_id VARCHAR(15)
    COMMENT "this is the genetic_nomenclature_id field.",
  
  immunology_gene_symbol VARCHAR(100)
    COMMENT "this is the immunology_gene_symbol field.",
  
  ix_synonyms TEXT
    COMMENT "this is the ix_synonyms field.",
  
  link VARCHAR(2000)
    COMMENT "this is the link field.",
  
  mesh_id VARCHAR(10)
    COMMENT "this is the mesh_id field.",
  
  mesh_name VARCHAR(255)
    COMMENT "this is the mesh_name field.",
  
  official_gene_name VARCHAR(255)
    COMMENT "this is the official_gene_name field.",
  
  omim_id VARCHAR(50)
    COMMENT "this is the omim_id field.",
  
  ortholog_ids VARCHAR(100)
    COMMENT "this is the ortholog_ids field.",
  
  protein_ontology_id VARCHAR(15)
    COMMENT "this is the protein_ontology_id field.",
  
  protein_ontology_name VARCHAR(100)
    COMMENT "this is the protein_ontology_name field.",
  
  protein_ontology_synonyms TEXT
    COMMENT "this is the protein_ontology_synonyms field.",
  
  protein_ontology_url VARCHAR(500)
    COMMENT "this is the protein_ontology_url field.",
  
  shen_orr_id VARCHAR(10)
    COMMENT "this is the shen_orr_id field.",
  
  taxonomy_id VARCHAR(10)
    COMMENT "this is the taxonomy_id field.",
  
  typographical_variations VARCHAR(1000)
    COMMENT "this is the typographical_variations field.",
  
  uniprot_alt_prot_names TEXT
    COMMENT "this is the uniprot_alt_prot_names field.",
  
  uniprot_id VARCHAR(20)
    COMMENT "this is the uniprot_id field.",
  
  uniprot_protein_name VARCHAR(255)
    COMMENT "this is the uniprot_protein_name field.",
  
  unique_id VARCHAR(10)
    COMMENT "this is the unique_id field.",
  
  PRIMARY KEY (analyte_accession)
) ENGINE=InnoDB DEFAULT CHARSET=utf8
  COMMENT "this is the lk_analyte table.";

CREATE UNIQUE INDEX idx_lk_analyte_1 on lk_analyte(analyte_preferred);
