#!/usr/bin/env python3
"""Gera as quatro árvores de especialização em src/main/resources/trees/<profissão>.yml.

Todas têm a mesma forma: tronco comum (5 nós), 3 caminhos (4 nós cada) e 2 especializações por
caminho (3 nós + o nó final, aos 1000). Um ramo inteiro custa 22 pontos e o jogador só tem 20,
então dois artesãos do mesmo ramo ainda ficam diferentes.

Os efeitos são números somados pelo plugin (ProfessionService#modifier); cada módulo de profissão
lê as chaves que usa. Edite a tabela e rode:  python tools/gerar_arvores.py
"""
import os

SAIDA = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "trees")

# (nome, descrição, tipo, {efeito: valor})
N = lambda nome, desc, efeito, tipo="passivo": (nome, desc, tipo, efeito)

ARVORES = {
    "ferreiro": {
        "tronco": [
            N("Mão firme", "Janela da batida perfeita 5% maior.", {"janela_perfeita": 0.05}),
            N("Fole eficiente", "O calor cai 10% mais devagar.", {"calor_estavel": 0.10}),
            N("Olho para o metal", "Reparo 5% mais barato.", {"custo_reparo": -0.05}),
            N("Aproveitar sobras", "10% de chance de poupar um material na forja.", {"material_poupado": 0.10}),
            N("Batida ritmada", "Janela da batida boa 10% maior.", {"janela_boa": 0.10}),
        ],
        "caminhos": {
            "armeiro": ("Armeiro", "Armas de rank B ou maior", [
                N("Projetos de armas", "Libera os projetos de armas de rank B.", {"desbloqueia_armas_b": 1}, "desbloqueio"),
                N("Fio apurado", "+3% de qualidade em armas.", {"qualidade_armas": 0.03}),
                N("Núcleo equilibrado", "O atributo dominante fica 5% mais forte.", {"atributo_dominante": 0.05}),
                N("Forja rápida", "Armas pedem 10% menos batidas.", {"batidas": -0.10}),
            ], {
                "laminas": ("Lâminas", [
                    N("Projetos de lâminas", "Libera espadas, floretes e adagas de rank A.", {"desbloqueia_laminas_a": 1}, "desbloqueio"),
                    N("Têmpera precisa", "Janela da têmpera 25% maior.", {"janela_tempera": 0.25}),
                    N("Lâmina viva", "+1 tentativa de aprimoramento em lâminas.", {"tentativas_extras": 1}),
                ], N("Golpe de Mestre", "Uma batida perfeita garantida por forja.", {"tecnica_golpe_de_mestre": 1}, "tecnica")),
                "armas_pesadas": ("Armas pesadas", [
                    N("Projetos pesados", "Libera machados, lanças e espadas de duas mãos de rank A.", {"desbloqueia_pesadas_a": 1}, "desbloqueio"),
                    N("Braço forte", "Itens grandes pedem um reaquecimento a menos.", {"reaquecer": -1}),
                    N("Peso certo", "O atributo Peso sobe 10% a mais.", {"atributo_peso": 0.10}),
                ], N("Martelada Esmagadora", "A última batida vale por três.", {"tecnica_martelada": 1}, "tecnica")),
            }),
            "armoreiro": ("Armoreiro", "Armaduras e escudos de rank B ou maior", [
                N("Projetos de armaduras", "Libera armaduras e escudos de rank B.", {"desbloqueia_armaduras_b": 1}, "desbloqueio"),
                N("Rebites firmes", "+5% de durabilidade em armaduras.", {"durabilidade_armaduras": 0.05}),
                N("Forro bem cortado", "+3% de qualidade em armaduras.", {"qualidade_armaduras": 0.03}),
                N("Trabalho de placas", "Armaduras pedem 10% menos batidas.", {"batidas": -0.10}),
            ], {
                "placas": ("Placas", [
                    N("Projetos de placas", "Libera armaduras de placas e escudos de rank A.", {"desbloqueia_placas_a": 1}, "desbloqueio"),
                    N("Aço dobrado", "+10% de defesa em placas.", {"defesa_placas": 0.10}),
                    N("Sem frestas", "Uma trinca a mais antes de a peça quebrar.", {"trincas_extras": 1}),
                ], N("Muralha", "A peça sai com a durabilidade dobrada.", {"tecnica_muralha": 1}, "tecnica")),
                "couracas_leves": ("Couraças leves", [
                    N("Projetos de couraças", "Libera couraças leves de rank A.", {"desbloqueia_couracas_a": 1}, "desbloqueio"),
                    N("Corte leve", "Couraças não reduzem a velocidade.", {"sem_penalidade_velocidade": 1}),
                    N("Costura dupla", "+10% de durabilidade em couraças.", {"durabilidade_couracas": 0.10}),
                ], N("Segunda Pele", "A couraça dá um bônus de esquiva.", {"tecnica_segunda_pele": 1}, "tecnica")),
            }),
            "refinador": ("Refinador", "Aprimoramento acima de +5, reparo de itens épicos e lendários", [
                N("Aprimoramento avançado", "Libera o aprimoramento acima de +5.", {"desbloqueia_aprimoramento_alto": 1}, "desbloqueio"),
                N("Pedra de amolar", "+3% de chance de aprimoramento.", {"chance_aprimoramento": 0.03}),
                N("Reparo cuidadoso", "Reparo 10% mais barato.", {"custo_reparo": -0.10}),
                N("Mão leve", "Falha de aprimoramento perde menos materiais.", {"materiais_na_falha": -0.25}),
            ], {
                "afiador": ("Afiador", [
                    N("Fio perfeito", "+5% de chance em Afiação e Precisão.", {"chance_afiacao": 0.05}),
                    N("Sem recuo", "A falha nunca baixa o atributo.", {"falha_sem_queda": 1}),
                    N("Polimento", "+1 tentativa de aprimoramento em qualquer arma.", {"tentativas_extras": 1}),
                ], N("Fio Lendário", "Uma vez por arma, um aprimoramento com sucesso garantido.", {"tecnica_fio_lendario": 1}, "tecnica")),
                "restaurador": ("Restaurador", [
                    N("Reparo de raros", "Libera o reparo de itens épicos e lendários.", {"desbloqueia_reparo_epico": 1}, "desbloqueio"),
                    N("Como novo", "O reparo devolve 10% de durabilidade máxima perdida.", {"restaura_maximo": 0.10}),
                    N("Economia de metal", "Reparo usa 20% menos material.", {"material_reparo": -0.20}),
                ], N("Renascer do Aço", "Restaura um item quebrado de vez.", {"tecnica_renascer": 1}, "tecnica")),
            }),
        },
    },
    "culinaria": {
        "tronco": [
            N("Faca afiada", "Janela de acerto do corte 10% maior.", {"janela_corte": 0.10}),
            N("Fogo baixo", "A faixa certa do fogo fica 10% maior.", {"faixa_fogo": 0.10}),
            N("Sem desperdício", "10% de chance de poupar um ingrediente.", {"ingrediente_poupado": 0.10}),
            N("Paladar", "+3% de chance de prato excelente.", {"chance_excelente": 0.03}),
            N("Cozinha limpa", "Preparo 10% mais rápido.", {"tempo_preparo": -0.10}),
        ],
        "caminhos": {
            "chef_de_banquete": ("Chef de banquete", "Pratos de panela com buff para a party inteira", [
                N("Receitas de panela", "Libera os pratos de panela de rank B.", {"desbloqueia_panela_b": 1}, "desbloqueio"),
                N("Panela grande", "+1 porção em pratos de panela.", {"porcoes": 1}),
                N("Servir quente", "Buffs de panela duram 10% mais.", {"duracao_buff": 0.10}),
                N("Mesa farta", "O buff alcança aliados a mais 5 blocos.", {"alcance_buff": 5}),
            ], {
                "festins": ("Festins", [
                    N("Receitas de festim", "Libera os festins de rank A.", {"desbloqueia_festim_a": 1}, "desbloqueio"),
                    N("Mais um prato", "+2 porções em festins.", {"porcoes": 2}),
                    N("Brinde", "Festins dão também um pouco de regeneração.", {"festim_regeneracao": 1}),
                ], N("Banquete Real", "Um festim dá dois buffs ao mesmo tempo.", {"tecnica_banquete_real": 1}, "tecnica")),
                "cozinha_de_campanha": ("Cozinha de campanha", [
                    N("Fogueira improvisada", "Cozinha pratos de panela em qualquer fogueira.", {"desbloqueia_fogueira": 1}, "desbloqueio"),
                    N("Panela de ferro", "Pratos de campanha nunca queimam na etapa do fogo.", {"nunca_queima": 1}),
                    N("Marmita", "Pratos de campanha duram 20% mais.", {"duracao_buff": 0.20}),
                ], N("Rancho de Batalha", "O prato pode ser comido em combate, na hora.", {"tecnica_rancho": 1}, "tecnica")),
            }),
            "gourmet": ("Gourmet", "Pratos individuais de rank A e S", [
                N("Alta cozinha", "Libera os pratos individuais de rank B.", {"desbloqueia_individual_b": 1}, "desbloqueio"),
                N("Tempero exato", "Janela do tempero 15% maior.", {"janela_tempero": 0.15}),
                N("Apresentação", "+5% de chance de excelente no empratar.", {"chance_excelente": 0.05}),
                N("Sabor profundo", "Buffs individuais 5% mais fortes.", {"forca_buff": 0.05}),
            ], {
                "carnes": ("Carnes", [
                    N("Cortes nobres", "Libera os pratos de carne de rank A e S.", {"desbloqueia_carnes_a": 1}, "desbloqueio"),
                    N("Ponto certo", "A etapa do fogo dá bônus de qualidade.", {"bonus_fogo": 0.10}),
                    N("Marinada", "Buffs de força e vida 10% mais fortes.", {"forca_buff_carne": 0.10}),
                ], N("Ragu Lendário", "Um prato de carne excelente dá o buff por uma hora.", {"tecnica_ragu": 1}, "tecnica")),
                "doces_e_bebidas": ("Doces e bebidas", [
                    N("Confeitaria", "Libera doces e bebidas de rank A e S.", {"desbloqueia_doces_a": 1}, "desbloqueio"),
                    N("Calda perfeita", "A etapa de mexer fica 20% mais fácil.", {"janela_mexer": 0.20}),
                    N("Doce energia", "Buffs de velocidade e XP 10% mais fortes.", {"forca_buff_doce": 0.10}),
                ], N("Néctar", "Uma bebida excelente não ocupa o lugar do buff de comida.", {"tecnica_nectar": 1}, "tecnica")),
            }),
            "provisionador": ("Provisionador", "Comida que não estraga e buffs longos", [
                N("Conservar", "Libera as provisões de rank B.", {"desbloqueia_provisoes_b": 1}, "desbloqueio"),
                N("Bem embalado", "Buffs de provisões duram 15% mais.", {"duracao_buff": 0.15}),
                N("Estoque", "+1 porção em provisões.", {"porcoes": 1}),
                N("Compra no atacado", "Ingredientes de loja 10% mais baratos.", {"desconto_ingredientes": 0.10}),
            ], {
                "conservas": ("Conservas", [
                    N("Potes e vidros", "Libera as conservas de rank A.", {"desbloqueia_conservas_a": 1}, "desbloqueio"),
                    N("Lacre", "Conservas nunca estragam na banca.", {"sem_validade": 1}),
                    N("Receita de família", "Conservas rendem o dobro de porções.", {"porcoes_dobro": 1}),
                ], N("Despensa Infinita", "Uma conserva excelente pode ser comida duas vezes.", {"tecnica_despensa": 1}, "tecnica")),
                "racoes_de_viagem": ("Rações de viagem", [
                    N("Ração seca", "Libera as rações de viagem de rank A.", {"desbloqueia_racoes_a": 1}, "desbloqueio"),
                    N("Leve na mochila", "Rações empilham até 64.", {"empilha": 1}),
                    N("Fôlego longo", "Buffs de ração duram 30% mais.", {"duracao_buff": 0.30}),
                ], N("Pão do Viajante", "A ração tira a fome por um dia inteiro.", {"tecnica_pao_do_viajante": 1}, "tecnica")),
            }),
        },
    },
    "pesca": {
        "tronco": [
            N("Pulso firme", "Faixa verde da tensão 5% maior.", {"faixa_verde": 0.05}),
            N("Linha encerada", "Linha 10% mais resistente.", {"resistencia_linha": 0.10}),
            N("Paciência", "A janela da fisgada fica 10% maior.", {"janela_fisgada": 0.10}),
            N("Isca bem presa", "10% de chance de não gastar a isca.", {"isca_poupada": 0.10}),
            N("Olho na boia", "+3% de chance de peixe incomum.", {"chance_incomum": 0.03}),
        ],
        "caminhos": {
            "aguas_profundas": ("Águas profundas", "Zona funda e o Senhor do Lago", [
                N("Lançamento longo", "Libera a zona funda dos pontos de pesca.", {"desbloqueia_fundo": 1}, "desbloqueio"),
                N("Chumbada pesada", "A barra de força enche 10% mais rápido.", {"forca_lancamento": 0.10}),
                N("Linha de aço", "Linha 15% mais resistente.", {"resistencia_linha": 0.15}),
                N("Conhecer o fundo", "+5% de chance de peixe raro na zona funda.", {"chance_raro": 0.05}),
            ], {
                "cacador_de_lendarios": ("Caçador de lendários", [
                    N("Sinal na água", "Libera a chance do peixe lendário de cada andar.", {"desbloqueia_lendario": 1}, "desbloqueio"),
                    N("Contra a arrancada", "Arrancadas sobem 25% menos a tensão.", {"arrancada": -0.25}),
                    N("Isca viva", "+50% de chance de lendário com a isca certa.", {"chance_lendario": 0.50}),
                ], N("Fisgada do Senhor do Lago", "O Senhor do Lago sai da água com 20% menos vida.", {"tecnica_senhor_do_lago": 1}, "tecnica")),
                "mergulhador": ("Mergulhador", [
                    N("Fôlego", "Libera a pesca de mão em águas fundas.", {"desbloqueia_mergulho": 1}, "desbloqueio"),
                    N("Olhos abertos", "Vê o tipo do peixe antes de fisgar.", {"ver_peixe": 1}),
                    N("Braçada forte", "O peixe cansa 15% mais rápido.", {"cansar_peixe": 0.15}),
                ], N("Arpão Certeiro", "Uma vez por pescaria, pega o peixe sem a fase de tensão.", {"tecnica_arpao": 1}, "tecnica")),
            }),
            "mestre_de_iscas": ("Mestre de iscas", "Iscas próprias, frescor dobrado na banca", [
                N("Fazer iscas", "Libera a produção de iscas de rank B.", {"desbloqueia_iscas_b": 1}, "desbloqueio"),
                N("Isca bem feita", "Iscas próprias dão +5% de chance de raro.", {"chance_raro": 0.05}),
                N("Gelo na caixa", "O peixe fica fresco 50% mais tempo.", {"frescor": 0.50}),
                N("Mão cheia", "Cada produção rende 1 isca a mais.", {"iscas_extras": 1}),
            ], {
                "criador_de_iscas": ("Criador de iscas", [
                    N("Iscas raras", "Libera as iscas de rank A e a isca de lendário.", {"desbloqueia_iscas_a": 1}, "desbloqueio"),
                    N("Receita secreta", "20% de chance de não gastar a isca.", {"isca_poupada": 0.20}),
                    N("Cheiro forte", "O peixe morde 20% mais rápido.", {"tempo_mordida": -0.20}),
                ], N("Isca Dourada", "Uma isca que garante um peixe raro.", {"tecnica_isca_dourada": 1}, "tecnica")),
                "peixeiro": ("Peixeiro", [
                    N("Banca maior", "A banca de peixe guarda o dobro.", {"banca_dobro": 1}, "desbloqueio"),
                    N("Sempre fresco", "O frescor do peixe dura o dobro.", {"frescor": 1.0}),
                    N("Bom de venda", "Vende peixe às lojas por 10% a mais.", {"preco_venda": 0.10}),
                ], N("Pesca do Dia", "O primeiro peixe de cada dia vale o dobro.", {"tecnica_pesca_do_dia": 1}, "tecnica")),
            }),
            "caca_tesouros": ("Caça-tesouros", "Pescar baús, minérios e reagentes", [
                N("Anzol de garra", "Libera a pesca de baús e materiais.", {"desbloqueia_tesouros": 1}, "desbloqueio"),
                N("Sorte de pescador", "+3% de chance de tesouro.", {"chance_tesouro": 0.03}),
                N("Rede fina", "+1 material por tesouro.", {"materiais_extras": 1}),
                N("Fundo revirado", "Tesouros aparecem também na zona do meio.", {"tesouro_no_meio": 1}),
            ], {
                "baus": ("Baús", [
                    N("Baús afundados", "Libera os baús de rank A.", {"desbloqueia_baus_a": 1}, "desbloqueio"),
                    N("Cadeado fraco", "Baús pescados têm 1 item a mais.", {"itens_bau": 1}),
                    N("Mapa molhado", "+5% de chance de tesouro.", {"chance_tesouro": 0.05}),
                ], N("Tesouro do Naufrágio", "Uma vez por dia, um baú garantido.", {"tecnica_naufragio": 1}, "tecnica")),
                "materiais_raros": ("Materiais raros", [
                    N("Minério do fundo", "Libera a pesca de minérios e reagentes de rank A.", {"desbloqueia_materiais_a": 1}, "desbloqueio"),
                    N("Peneira", "+1 material por pescaria de material.", {"materiais_extras": 1}),
                    N("Bom faro", "Materiais pescados têm rank maior.", {"rank_materiais": 1}),
                ], N("Veio Submerso", "Uma pescaria de material rende o triplo.", {"tecnica_veio": 1}, "tecnica")),
            }),
        },
    },
    "alquimia": {
        "tronco": [
            N("Mão de almofariz", "O ritmo de moer fica 10% mais fácil.", {"janela_moer": 0.10}),
            N("Chama constante", "Faixa certa da temperatura 10% maior.", {"faixa_temperatura": 0.10}),
            N("Colher com cuidado", "+10% de reagentes na coleta.", {"colheita": 0.10}),
            N("Medida exata", "10% de chance de poupar um reagente.", {"reagente_poupado": 0.10}),
            N("Vidro limpo", "+3% de pureza inicial.", {"pureza": 0.03}),
        ],
        "caminhos": {
            "curandeiro": ("Curandeiro", "Cura de grau alto, cristais de cura e antídoto", [
                N("Fórmulas de cura", "Libera as poções de cura de grau 3.", {"desbloqueia_cura_3": 1}, "desbloqueio"),
                N("Erva certa", "Poções de cura 5% mais fortes.", {"forca_cura": 0.05}),
                N("Dose generosa", "10% de chance de uma dose extra.", {"dose_extra": 0.10}),
                N("Sem amargor", "Poções impuras não dão efeito colateral.", {"sem_colateral": 1}),
            ], {
                "pocoes": ("Poções", [
                    N("Grande cura", "Libera as poções de cura de grau 4.", {"desbloqueia_cura_4": 1}, "desbloqueio"),
                    N("Ação rápida", "A cura das poções acontece em 8 s, não 10.", {"tempo_cura": -2}),
                    N("Frasco cheio", "+1 dose em poções de cura.", {"doses": 1}),
                ], N("Elixir", "Uma poção perfeita cura na hora, como um cristal.", {"tecnica_elixir": 1}, "tecnica")),
                "cristais": ("Cristais", [
                    N("Lapidar cristais", "Libera os cristais de cura e antídoto.", {"desbloqueia_cristais": 1}, "desbloqueio"),
                    N("Cristal limpo", "20% de chance de poupar o cristal bruto.", {"cristal_poupado": 0.20}),
                    N("Lapidação dupla", "10% de chance de sair dois cristais.", {"cristal_duplo": 0.10}),
                ], N("Cristal Puro", "O cristal feito tem metade da recarga.", {"tecnica_cristal_puro": 1}, "tecnica")),
            }),
            "toxicologista": ("Toxicologista", "Venenos e óleos de grau alto", [
                N("Fórmulas tóxicas", "Libera venenos e óleos de grau 3.", {"desbloqueia_toxico_3": 1}, "desbloqueio"),
                N("Luvas grossas", "A mistura não explode ao superaquecer uma vez.", {"explosao_perdoada": 1}),
                N("Concentrado", "Venenos e óleos 5% mais fortes.", {"forca_toxico": 0.05}),
                N("Gota a mais", "Venenos e óleos duram 2 golpes a mais.", {"golpes_extras": 2}),
            ], {
                "venenos": ("Venenos", [
                    N("Venenos fortes", "Libera os venenos de grau 4 e o de paralisia.", {"desbloqueia_veneno_4": 1}, "desbloqueio"),
                    N("Efeito demorado", "O veneno dura 25% mais no alvo.", {"duracao_veneno": 0.25}),
                    N("Mão segura", "Aplicar veneno na arma não gasta tempo.", {"aplicar_rapido": 1}),
                ], N("Peçonha", "Um veneno perfeito ignora metade da resistência do mob.", {"tecnica_peconha": 1}, "tecnica")),
                "oleos_de_arma": ("Óleos de arma", [
                    N("Óleos raros", "Libera os óleos de grau 4.", {"desbloqueia_oleo_4": 1}, "desbloqueio"),
                    N("Camada grossa", "Óleos duram 5 golpes a mais.", {"golpes_extras": 5}),
                    N("Mistura fina", "Óleos 10% mais fortes.", {"forca_oleo": 0.10}),
                ], N("Fio de Fogo", "Um óleo perfeito dá também dano em área.", {"tecnica_fio_de_fogo": 1}, "tecnica")),
            }),
            "artifice": ("Artífice", "Frascos de área e insumos para as outras profissões", [
                N("Fórmulas de oficina", "Libera arremessáveis e insumos de grau 3.", {"desbloqueia_oficina_3": 1}, "desbloqueio"),
                N("Vidro fino", "Frascos arremessáveis vão 20% mais longe.", {"alcance_frasco": 0.20}),
                N("Lote maior", "+1 unidade por produção de insumo.", {"insumos_extras": 1}),
                N("Bancada arrumada", "Preparo 10% mais rápido.", {"tempo_preparo": -0.10}),
            ], {
                "arremessaveis": ("Arremessáveis", [
                    N("Frascos de área", "Libera os arremessáveis de grau 4.", {"desbloqueia_frasco_4": 1}, "desbloqueio"),
                    N("Nuvem larga", "A área dos frascos fica 25% maior.", {"area_frasco": 0.25}),
                    N("Estilhaço", "Frascos duram 25% mais no chão.", {"duracao_frasco": 0.25}),
                ], N("Tempestade de Vidro", "Um frasco perfeito se divide em três.", {"tecnica_tempestade": 1}, "tecnica")),
                "insumos": ("Insumos", [
                    N("Insumos raros", "Libera a isca avançada, o tempero raro e o pó de têmpera.", {"desbloqueia_insumos": 1}, "desbloqueio"),
                    N("Pó fino", "O pó de têmpera alarga a janela 50% a mais.", {"forca_po_tempera": 0.50}),
                    N("Rende mais", "+2 unidades por produção de insumo.", {"insumos_extras": 2}),
                ], N("Catalisador Universal", "Um insumo que serve de catalisador em qualquer profissão.", {"tecnica_catalisador": 1}, "tecnica")),
            }),
        },
    },
}

TRONCO_PROF = [0, 50, 100, 150, 200]
CAMINHO_PROF = [250, 300, 350, 450]
CAMINHO_CUSTO = [1, 2, 2, 2]
ESPEC_PROF = [500, 600, 750]
ESPEC_CUSTO = [2, 2, 3]


def chave(nome):
    troca = str.maketrans("áàâãéêíóôõúç ", "aaaaeeiooouc_")
    return "".join(c for c in nome.lower().translate(troca) if c.isalnum() or c == "_")


def no(linhas, ident, dados, prof, custo, caminho=None, espec=None, requer=None):
    nome, desc, tipo, efeito = dados
    linhas.append(f"  {ident}:")
    linhas.append(f'    nome: "{nome}"')
    linhas.append(f'    descricao: "{desc}"')
    linhas.append(f"    tipo: {tipo}")
    if caminho:
        linhas.append(f"    caminho: {caminho}")
    if espec:
        linhas.append(f"    especializacao: {espec}")
    linhas.append(f"    proficiencia: {prof}")
    linhas.append(f"    custo: {custo}")
    if requer:
        linhas.append(f"    requer: [{requer}]")
    linhas.append("    efeito: { " + ", ".join(f"{k}: {v}" for k, v in efeito.items()) + " }")


def gerar(prof, arvore):
    out = [
        "# ============================================================",
        f"#  Árvore de especialização — {prof}",
        "#  Gerada por tools/gerar_arvores.py (no projeto); pode ser editada à mão no servidor.",
        "#",
        "#  caminhos: escolhidos aos 250 de proficiência (um entre três, sem volta);",
        "#            cada um tem duas especializações, escolhidas aos 500 (uma, sem volta).",
        "#  nos:      tipo passivo | desbloqueio | tecnica; \"requer\" são nós que vêm antes;",
        "#            \"efeito\" são números somados para quem comprou o nó.",
        "#  Pontos de ofício: 1 a cada 50 de proficiência (20 no total).",
        "# ============================================================",
        "caminhos:",
    ]
    for cid, (cnome, exclusivo, _, especs) in arvore["caminhos"].items():
        out.append(f"  {cid}:")
        out.append(f'    nome: "{cnome}"')
        out.append(f'    exclusivo: "{exclusivo}"')
        out.append("    especializacoes:")
        for eid, (enome, _, _) in especs.items():
            out.append(f'      {eid}: "{enome}"')
    out.append("")
    out.append("nos:")
    out.append("  # ----- tronco comum -----")
    for i, dados in enumerate(arvore["tronco"]):
        no(out, chave(dados[0]), dados, TRONCO_PROF[i], 1)
    for cid, (cnome, _, nos, especs) in arvore["caminhos"].items():
        out.append(f"  # ----- caminho: {cnome} -----")
        anterior = None
        for i, dados in enumerate(nos):
            ident = chave(dados[0])
            no(out, ident, dados, CAMINHO_PROF[i], CAMINHO_CUSTO[i], caminho=cid, requer=anterior)
            anterior = ident if i == 0 else anterior  # todos os nós do caminho pedem o primeiro (o desbloqueio)
        for eid, (enome, enos, final) in especs.items():
            out.append(f"  # ----- especialização: {enome} -----")
            primeiro = None
            for i, dados in enumerate(enos):
                ident = chave(dados[0])
                no(out, ident, dados, ESPEC_PROF[i], ESPEC_CUSTO[i], caminho=cid, espec=eid, requer=primeiro)
                primeiro = primeiro or ident
            no(out, chave(final[0]), final, 1000, 3, caminho=cid, espec=eid, requer=primeiro)
    return "\n".join(out) + "\n"


def main():
    os.makedirs(SAIDA, exist_ok=True)
    for prof, arvore in ARVORES.items():
        with open(os.path.join(SAIDA, prof + ".yml"), "w", encoding="utf-8", newline="\n") as f:
            f.write(gerar(prof, arvore))
        print(prof)


if __name__ == "__main__":
    main()
