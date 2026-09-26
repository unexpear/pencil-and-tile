"""Builds Lone Letter's category answer lists from Open English WordNet (CC BY 4.0).

usage: python tools/words/build_categories.py path/to/english-wordnet-2025.xml.gz

Each category is one or more WordNet synsets; its answers are every word or name below them in the
"is a kind of" and "is an instance of" trees. Everyday answers from the hand-written lists below
score above zero (the more obvious, the higher), and only those are ever picked by the computer players.
Writes sudoku-engine/src/main/resources/categories/en.tsv:  id  label  answer:score,answer:score,...
"""
import gzip, os, re, sys, xml.etree.ElementTree as ET

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
OUT = os.path.join(ROOT, 'sudoku-engine', 'src', 'main', 'resources', 'categories', 'en.tsv')

CATEGORIES = [
    ('animals', 'Animals', ['oewn-01864419-n', 'oewn-01505702-n', 'oewn-02514684-n', 'oewn-01663732-n', 'oewn-02162607-n']),
    ('birds', 'Birds', ['oewn-01505702-n']),
    ('fish', 'Fish and sea creatures', ['oewn-02514684-n']),
    ('insects', 'Insects and bugs', ['oewn-02162607-n']),
    ('dogs', 'Kinds of dog', ['oewn-02086723-n']),
    ('fruit', 'Fruit', ['oewn-07721676-n']),
    ('vegetables', 'Vegetables', ['oewn-07723196-n']),
    ('dishes', 'Foods and dishes', ['oewn-07572999-n']),
    ('drinks', 'Drinks', ['oewn-07897775-n']),
    ('desserts', 'Desserts and sweets', ['oewn-07625449-n', 'oewn-07612936-n']),
    ('cheeses', 'Cheeses', ['oewn-07866305-n']),
    ('herbs', 'Herbs and spices', ['oewn-07827392-n', 'oewn-07828160-n']),
    ('flowers', 'Flowers', ['oewn-11690372-n', 'oewn-11689786-n']),
    ('trees', 'Trees', ['oewn-13124818-n']),
    ('sports', 'Sports', ['oewn-00524569-n']),
    ('games', 'Games', ['oewn-00456623-n']),
    ('dances', 'Dances', ['oewn-00429255-n']),
    ('toys', 'Toys', ['oewn-03971038-n']),
    ('instruments', 'Musical instruments', ['oewn-03806455-n']),
    ('clothes', 'Clothes', ['oewn-03055525-n']),
    ('body', 'Parts of the body', ['oewn-05227735-n']),
    ('furniture', 'Furniture', ['oewn-03410635-n']),
    ('kitchen', 'Kitchen things', ['oewn-03626258-n']),
    ('tools', 'Tools', ['oewn-03494245-n']),
    ('vehicles', 'Vehicles and boats', ['oewn-04531608-n', 'oewn-02861626-n']),
    ('buildings', 'Buildings', ['oewn-02916498-n']),
    ('jobs', 'Jobs', ['oewn-09655706-n']),
    ('countries', 'Countries', []),
    ('cities', 'Cities', []),
    ('colors', 'Colours', ['oewn-04966849-n']),
    ('feelings', 'Feelings', ['oewn-00026390-n']),
    ('metals', 'Metals', ['oewn-14649636-n']),
]


# This WordNet has no place names, so countries and cities are listed by hand, best known first.
# Several names for one place are all fine answers.
PLACES = {
    'countries': """
        united states,usa,america,china,india,japan,france,germany,italy,spain,brazil,canada,mexico,russia,england,
        australia,egypt,greece,ireland,scotland,wales,britain,united kingdom,portugal,sweden,norway,denmark,finland,
        iceland,netherlands,holland,belgium,switzerland,austria,poland,turkey,israel,iran,iraq,kenya,nigeria,
        south africa,argentina,chile,peru,colombia,venezuela,cuba,jamaica,new zealand,korea,south korea,north korea,
        vietnam,thailand,indonesia,philippines,pakistan,afghanistan,saudi arabia,morocco,ethiopia,ghana,ukraine,
        hungary,czech republic,czechia,romania,bulgaria,croatia,serbia,bolivia,ecuador,uruguay,paraguay,panama,
        costa rica,nepal,bangladesh,sri lanka,malaysia,singapore,mongolia,tibet,taiwan,syria,jordan,lebanon,kuwait,
        qatar,oman,yemen,libya,tunisia,algeria,sudan,somalia,uganda,tanzania,rwanda,zambia,zimbabwe,botswana,
        namibia,angola,mozambique,madagascar,cameroon,senegal,mali,niger,chad,congo,gabon,togo,benin,liberia,
        sierra leone,guinea,gambia,mauritania,burkina faso,ivory coast,eritrea,djibouti,burundi,malawi,lesotho,
        eswatini,swaziland,mauritius,seychelles,comoros,cape verde,luxembourg,monaco,liechtenstein,andorra,
        san marino,malta,cyprus,estonia,latvia,lithuania,belarus,moldova,georgia,armenia,azerbaijan,kazakhstan,
        uzbekistan,turkmenistan,kyrgyzstan,tajikistan,slovakia,slovenia,bosnia,montenegro,albania,macedonia,
        north macedonia,kosovo,vatican,cambodia,laos,myanmar,burma,brunei,east timor,bhutan,maldives,fiji,samoa,
        tonga,vanuatu,papua new guinea,solomon islands,kiribati,tuvalu,nauru,palau,micronesia,marshall islands,
        haiti,dominican republic,dominica,bahamas,barbados,trinidad,grenada,belize,honduras,guatemala,el salvador,
        nicaragua,guyana,suriname,united arab emirates,bahrain,equatorial guinea,central african republic,
        south sudan,guinea bissau,sao tome,antigua,saint lucia,saint kitts,saint vincent,greenland
    """,
    'cities': """
        london,paris,new york,tokyo,rome,berlin,madrid,sydney,chicago,los angeles,moscow,beijing,cairo,dublin,
        toronto,boston,miami,dallas,houston,seattle,denver,atlanta,detroit,vegas,las vegas,san francisco,
        washington,philadelphia,phoenix,orlando,nashville,memphis,austin,portland,baltimore,cleveland,pittsburgh,
        minneapolis,new orleans,san diego,honolulu,vancouver,montreal,ottawa,calgary,edinburgh,glasgow,manchester,
        liverpool,birmingham,leeds,bristol,cardiff,belfast,oxford,cambridge,york,amsterdam,brussels,vienna,prague,
        budapest,warsaw,athens,lisbon,barcelona,seville,milan,venice,florence,naples,munich,hamburg,frankfurt,
        cologne,zurich,geneva,stockholm,oslo,copenhagen,helsinki,istanbul,jerusalem,dubai,delhi,new delhi,mumbai,
        bombay,kolkata,calcutta,bangalore,chennai,karachi,lahore,shanghai,hong kong,seoul,osaka,kyoto,bangkok,
        singapore,manila,jakarta,hanoi,melbourne,brisbane,perth,adelaide,auckland,wellington,mexico city,
        havana,lima,bogota,santiago,buenos aires,rio de janeiro,rio,sao paulo,caracas,quito,montevideo,nairobi,
        lagos,cape town,johannesburg,casablanca,marrakech,tunis,accra,addis ababa,kinshasa,dakar,baghdad,tehran,
        kabul,riyadh,mecca,beirut,damascus,amman,ankara,kiev,kyiv,minsk,riga,tallinn,vilnius,bucharest,sofia,
        belgrade,zagreb,krakow,salzburg,lyon,marseille,nice,bordeaux,toulouse,porto,valencia,bilbao,turin,
        genoa,bologna,palermo,verona,antwerp,rotterdam,the hague,bern,basel,dresden,leipzig,stuttgart,
        nottingham,sheffield,newcastle,brighton,bath,aberdeen,cork,galway,quebec,winnipeg,halifax,anchorage,
        sacramento,san jose,salt lake city,kansas city,st louis,cincinnati,columbus,indianapolis,milwaukee,
        buffalo,albany,charlotte,raleigh,richmond,tampa,jacksonville,san antonio,el paso,tucson,albuquerque,
        omaha,oklahoma city,tulsa,louisville,savannah,charleston,hartford,providence,
        taipei,shenzhen,guangzhou,chengdu,wuhan,xian,nanjing,hangzhou,macau,pyongyang,ulaanbaatar,kathmandu,
        dhaka,colombo,yangon,phnom penh,kuala lumpur,ho chi minh city,saigon,nagoya,yokohama,sapporo,hiroshima,
        busan,islamabad,doha,muscat,abu dhabi,tel aviv,alexandria,khartoum,kampala,dar es salaam,harare,
        lusaka,luanda,durban,pretoria,tripoli,algiers,rabat,fez,tangier,timbuktu,zanzibar,reykjavik,valletta,
        monaco,luxembourg,edmonton,victoria,hobart,darwin,canberra,christchurch,suva,kingston,nassau,
        san juan,panama city,guatemala city,managua,la paz,asuncion,brasilia,recife,salvador,cusco,cartagena,
        medellin,guadalajara,monterrey,cancun,acapulco,tijuana
    """,
}

# Everyday answers, most obvious first, that the computer players pick from. They count as answers even where
# WordNet files them elsewhere. Rare, technical and multiword answers from WordNet are accepted from players
# but never picked. An underscore joins a name of several words.
PICKS = {
    'animals': """cat dog horse cow pig sheep goat lion tiger bear wolf fox rabbit mouse rat deer elephant giraffe
        zebra monkey gorilla kangaroo koala panda camel donkey bat beaver badger otter seal whale dolphin shark
        frog toad snake lizard turtle tortoise crocodile alligator hippo rhino leopard cheetah jaguar hyena moose
        elk bison buffalo ox mole hedgehog squirrel chipmunk hamster ferret weasel skunk raccoon llama alpaca
        antelope gazelle ape baboon chimpanzee lemur sloth armadillo porcupine yak walrus penguin owl eagle
        iguana newt gecko cobra python emu ostrich octopus jellyfish crab lobster snail worm ant bee""",
    'birds': """robin sparrow crow eagle owl hawk parrot pigeon dove duck goose swan chicken hen turkey ostrich
        emu penguin flamingo peacock pelican heron stork crane seagull gull swallow swift wren finch canary
        blackbird thrush starling magpie jay raven rook falcon kestrel vulture condor kiwi puffin albatross
        woodpecker kingfisher hummingbird toucan cockatoo budgie macaw lark nightingale partridge pheasant quail
        grouse ptarmigan oriole cardinal bluebird goldfinch mallard moorhen coot osprey buzzard kite egret ibis""",
    'fish': """shark whale dolphin salmon trout tuna cod haddock herring sardine mackerel carp pike perch bass
        catfish goldfish eel ray stingray swordfish marlin halibut plaice sole flounder anchovy snapper grouper
        barracuda piranha minnow guppy octopus squid crab lobster shrimp prawn oyster clam mussel scallop
        jellyfish starfish seahorse seal walrus otter turtle urchin orca narwhal manatee krill angelfish clownfish
        tilapia sturgeon kipper whiting koi""",
    'insects': """ant bee wasp hornet fly mosquito moth butterfly beetle ladybird ladybug grasshopper cricket
        locust cockroach termite flea louse tick spider scorpion dragonfly damselfly earwig weevil aphid gnat
        midge firefly glowworm caterpillar maggot mantis centipede millipede bumblebee bedbug stick_insect cicada
        silverfish woodlouse hoverfly horsefly housefly bluebottle katydid larva""",
    'dogs': """poodle beagle boxer collie terrier spaniel labrador retriever dachshund pug bulldog greyhound
        whippet husky chihuahua corgi dalmatian doberman rottweiler mastiff shepherd pointer setter hound
        bloodhound basset schnauzer maltese pomeranian samoyed sheepdog akita shih_tzu labradoodle cockapoo
        malamute newfoundland weimaraner vizsla saluki airedale lurcher mongrel mutt pinscher""",
    'fruit': """apple banana orange pear grape lemon lime cherry peach plum apricot mango melon watermelon kiwi
        pineapple strawberry raspberry blueberry blackberry cranberry gooseberry currant fig date coconut
        papaya guava lychee pomegranate grapefruit tangerine clementine satsuma nectarine olive avocado tomato
        raisin sultana prune quince rhubarb passionfruit persimmon kumquat damson elderberry mulberry""",
    'vegetables': """carrot potato onion pea bean cabbage lettuce tomato cucumber pepper broccoli cauliflower
        spinach celery leek garlic corn sweetcorn pumpkin squash courgette zucchini marrow aubergine eggplant
        radish turnip parsnip beetroot beet swede yam kale sprout asparagus artichoke mushroom okra chard
        rocket shallot chive fennel lentil chickpea gherkin kohlrabi watercress endive chicory""",
    'dishes': """pizza pasta soup salad sandwich burger curry stew pie omelette spaghetti lasagne lasagna
        risotto noodles sushi taco burrito chili casserole roast kebab paella quiche stir_fry toast pancake
        waffle porridge cereal hotdog sausage steak meatball fajita nachos hummus falafel dumpling ravioli
        goulash chowder broth gumbo tart pastry pudding crumble custard trifle bagel muffin biscuit cracker
        omelet fritter jambalaya moussaka kedgeree""",
    'drinks': """water milk tea coffee juice lemonade cola soda wine beer cider cocoa smoothie milkshake
        squash punch lager ale whisky whiskey vodka gin rum brandy sherry port champagne cocktail tonic
        espresso latte cappuccino mocha shake nectar yogurt kefir cordial mead sake tequila bourbon kombucha""",
    'desserts': """cake pie tart pudding custard jelly ice_cream sorbet trifle crumble cheesecake
        brownie cookie biscuit doughnut donut muffin cupcake fudge toffee caramel chocolate candy lollipop
        marshmallow meringue mousse pavlova sundae waffle pancake crepe eclair macaron flapjack gingerbread
        jam scone shortbread strudel tiramisu truffle nougat licorice liquorice gumdrop sherbet popsicle""",
    'cheeses': """cheddar brie camembert stilton feta mozzarella parmesan gouda edam emmental gruyere ricotta
        mascarpone halloumi roquefort gorgonzola wensleydale cheshire lancashire leicester jarlsberg paneer
        provolone manchego pecorino colby monterey_jack cottage cream_cheese swiss havarti limburger""",
    'herbs': """basil mint parsley sage thyme rosemary oregano dill chive coriander cilantro tarragon bay
        pepper salt ginger garlic cinnamon nutmeg clove cumin paprika turmeric curry chilli chili saffron
        vanilla cardamom allspice fennel mustard caraway anise marjoram lemongrass sorrel horseradish""",
    'flowers': """rose tulip daisy lily daffodil sunflower orchid poppy violet iris lilac lavender carnation
        pansy peony marigold dahlia bluebell buttercup primrose snowdrop crocus hyacinth geranium jasmine
        magnolia camellia foxglove begonia petunia zinnia aster heather honeysuckle hibiscus lotus
        chrysanthemum gardenia anemone orchis cornflower clover forget_me_not freesia gladiolus""",
    'trees': """oak ash elm birch beech pine fir spruce cedar maple willow poplar yew holly larch sycamore
        chestnut walnut hazel alder aspen cypress redwood sequoia palm banyan baobab eucalyptus acacia
        apple pear cherry plum olive lemon orange fig magnolia hawthorn rowan juniper hemlock mahogany teak
        ebony lime linden bamboo cedar""",
    'sports': """football soccer rugby cricket tennis golf hockey baseball basketball netball volleyball
        badminton squash swimming running rowing sailing cycling boxing wrestling judo karate fencing archery
        skiing skating snowboarding surfing diving climbing gymnastics athletics polo lacrosse bowling darts
        snooker pool billiards handball softball triathlon marathon sprinting javelin hurdles kayaking canoeing
        jogging hiking""",
    'games': """chess checkers draughts monopoly scrabble poker bridge snap solitaire dominoes bingo tag
        hide_and_seek hopscotch marbles darts cards charades pictionary twister jenga cluedo clue backgammon
        mahjong sudoku crossword jigsaw tennis golf football cricket hangman noughts_and_crosses tic_tac_toe
        battleship yahtzee uno rummy canasta whist bowls croquet""",
    'dances': """waltz tango salsa ballet disco foxtrot rumba samba cha_cha jive swing polka jig reel
        flamenco hula conga limbo twist breakdance tap quickstep mambo bolero merengue lambada charleston
        hokey_cokey macarena ceilidh morris line_dance square_dance belly_dance ballroom""",
    'toys': """doll ball kite yoyo teddy puzzle jigsaw lego blocks bricks top rocket robot train car truck
        marbles skipping_rope hoop slinky rattle puppet frisbee scooter skateboard bike tricycle trampoline
        swing slide seesaw balloon whistle drum dollhouse dinosaur spinner rubik bubbles playdough crayons
        rocking_horse teddy_bear""",
    'instruments': """piano guitar violin drum flute trumpet trombone saxophone clarinet oboe bassoon cello
        harp harmonica accordion banjo ukulele mandolin tuba horn bugle recorder organ keyboard xylophone
        triangle tambourine cymbal bagpipes viola double_bass lute sitar synthesizer fiddle kazoo maracas
        bongo glockenspiel piccolo""",
    'clothes': """shirt trousers jeans dress skirt coat jacket jumper sweater hat cap scarf gloves socks shoes
        boots sandals tie belt vest shorts pyjamas pajamas blouse cardigan hoodie tracksuit suit uniform
        overalls apron gown robe raincoat anorak parka poncho kilt leggings tights bra slippers trainers
        sneakers blazer tuxedo bonnet beret beanie mittens waistcoat tunic toga sari kimono""",
    'body': """head hair face eye ear nose mouth lip tongue tooth teeth chin cheek neck shoulder arm elbow wrist
        hand finger thumb nail chest back stomach belly hip leg knee ankle foot toe heel heart lung liver
        kidney brain bone skull rib spine muscle skin blood vein throat jaw brow eyebrow eyelash forehead
        temple palm knuckle calf thigh shin waist navel""",
    'furniture': """chair table bed sofa couch desk wardrobe dresser cupboard bookcase shelf stool bench
        armchair cabinet chest drawers futon hammock rocking_chair recliner ottoman footstool sideboard
        dresser mirror lamp crib cot bunk_bed dining_table coffee_table nightstand tallboy settee divan
        hatstand mattress""",
    'kitchen': """kettle toaster oven fridge freezer microwave pan pot saucepan frying_pan wok knife fork
        spoon plate bowl cup mug glass jug teapot whisk ladle spatula grater peeler sieve colander blender
        mixer tin_opener corkscrew rolling_pin chopping_board tray scales timer apron dishwasher sink tap
        cooker hob grill toastie teaspoon napkin""",
    'tools': """hammer saw drill screwdriver spanner wrench pliers chisel file plane axe hatchet shovel spade
        rake hoe trowel fork shears secateurs scissors knife tape_measure level ruler clamp vice mallet
        crowbar ladder wheelbarrow sander grinder jigsaw nail_gun stapler pickaxe sickle scythe awl""",
    'vehicles': """car bus truck lorry van taxi train tram bicycle bike motorbike motorcycle scooter boat ship
        yacht canoe kayak raft ferry submarine plane jet helicopter rocket tractor ambulance jeep limousine
        caravan camper coach sledge sled skateboard tank carriage cart wagon barge tugboat hovercraft glider
        balloon airship moped rickshaw gondola dinghy trolley""",
    'buildings': """house school church hospital hotel castle palace tower barn shed garage library museum
        bank shop store office factory cinema theatre theater stadium temple mosque cathedral chapel
        lighthouse windmill cottage bungalow mansion apartment flat skyscraper warehouse prison station
        airport restaurant cafe pub inn motel igloo hut cabin tent pyramid fort farmhouse gym""",
    'jobs': """doctor nurse teacher farmer baker builder chef cook pilot driver dentist lawyer judge police
        officer firefighter fireman soldier sailor waiter waitress artist actor singer dancer writer author
        painter plumber electrician carpenter mechanic engineer scientist vet banker cashier clerk manager
        secretary librarian postman butcher grocer tailor barber hairdresser gardener cleaner janitor
        miner fisherman nanny model photographer reporter journalist architect accountant surgeon
        pharmacist professor tutor coach referee lifeguard ranger astronaut""",
    'colors': """red orange yellow green blue purple pink brown black white grey gray silver gold violet
        indigo turquoise teal navy maroon crimson scarlet beige cream ivory lilac lavender magenta cyan
        amber peach coral olive lime mint mustard tan khaki bronze copper ruby emerald jade plum baby_blue
        sky_blue navy_blue royal_blue pale_pink hot_pink lemon_yellow grass_green""",
    'feelings': """happy sad angry glad scared afraid calm tired bored excited nervous proud jealous lonely
        love hate joy fear anger hope pride shame guilt grief worry envy relief surprise delight gloom
        upset cross cheerful grumpy anxious brave shy silly thankful grateful content hurt worried
        frightened embarrassed curious confused hopeful peaceful""",
    'metals': """iron gold silver copper tin lead zinc aluminium aluminum steel brass bronze platinum nickel
        chrome chromium mercury titanium cobalt tungsten uranium magnesium sodium potassium calcium lithium
        pewter""",
}

def blocked():
    out = set()
    for line in open(os.path.join(HERE, 'blocked.txt'), encoding='utf-8'):
        line = line.strip().lower()
        if line and not line.startswith('#'):
            out.add(line)
    return out


def main():
    root = ET.parse(gzip.open(sys.argv[1])).getroot()
    lex = root.find('Lexicon')
    below = {}
    names = {}
    for e in lex.findall('LexicalEntry'):
        w = e.find('Lemma').get('writtenForm')
        for s in e.findall('Sense'):
            names.setdefault(s.get('synset'), []).append(w)
    for s in lex.findall('Synset'):
        sid = s.get('id')
        for r in s.findall('SynsetRelation'):
            t, target = r.get('relType'), r.get('target')
            # The tree is stored from either end, so follow both.
            if t in ('hyponym', 'instance_hyponym'): below.setdefault(sid, []).append(target)
            elif t in ('hypernym', 'instance_hypernym'): below.setdefault(target, []).append(sid)
    bad = blocked()

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, 'w', encoding='utf-8', newline='\n') as f:
        f.write('# Generated by tools/words/build_categories.py from Open English WordNet (CC BY 4.0).\n')
        for cid, label, roots in CATEGORIES:
            seen, stack, answers = set(), list(roots), {}
            if cid in PLACES:
                listed = [a.strip() for a in PLACES[cid].replace('\n', ',').split(',') if a.strip()]
            else:
                listed = [a.replace('_', ' ') for a in PICKS[cid].split()]
            # Everyday answers score by how obvious they are, so computer players reach for the first ones first.
            for k, a in enumerate(listed):
                answers.setdefault(a, max(1, 1000 - k * 4))
            while stack:
                sid = stack.pop()
                if sid in seen: continue
                seen.add(sid)
                stack.extend(below.get(sid, []))
                if sid in roots: continue
                for w in names.get(sid, []):
                    a = re.sub(r'[^a-z ]', '', w.lower().replace('-', ' ')).strip()
                    a = re.sub(r'\s+', ' ', a)
                    if not a or len(a) < 3 or len(a.split()) > 3 or any(p in bad for p in a.split()): continue
                    answers.setdefault(a, 0)
            ranked = sorted(answers.items(), key=lambda kv: (-kv[1], kv[0]))
            f.write('\t'.join([cid, label, ','.join(f'{a}:{s}' for a, s in ranked)]) + '\n')
            print(f'{label}: {len(ranked)} answers; top: {[a for a, _ in ranked[:8]]}')


if __name__ == '__main__':
    main()
