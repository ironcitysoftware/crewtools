./scripts/CatProto.sh $1 | grep employee_id | cut -d : -f 2 | cut -c 2- | sort

