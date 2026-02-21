Assuming there is an account with associated credentials already in the database:

```rest
POST http://localhost:8000/api/workflow/login/pass
Content-Type: application/json

{
    "username": "test",
    "password": "mish123"
}
```

We can get the current account information using the token
```rest
GET http://localhost:8000/api/accounts/@me
Authorization: Bearer eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJjbC5jYWRjYy5yYW1pdG9zIiwiZXhwIjoxNzcxNjM4ODMyLCJpYXQiOjE3NzE2MzcwMzIsCiAgImFjY291bnQiIDogewogICAgImlkIiA6IDUsCiAgICAiZGlzcGxheU5hbWUiIDogInRlc3QiLAogICAgInJvbGUiIDogewogICAgICAibm9uZSIgOiB7CiAgICAgICAgCiAgICAgIH0KICAgIH0sCiAgICAiY3JlYXRlZEF0IiA6ICIyMDI2LTAyLTIwVDIxOjA3OjU3LjM3Njk3MiIsCiAgICAidXBkYXRlZEF0IiA6ICIyMDI2LTAyLTIwVDIxOjA3OjU3LjM3Njk3MiIKICB9LAogICJtZXRob2QiIDogewogICAgIlBhc3N3b3JkIiA6IHsKICAgICAgCiAgICB9CiAgfQp9.I4beWbsMG5KiIcZOORDHC8Y4Vr3xhvKS_5WH_0xWzp0
```


We can see that we get 401 on validated errors:
```rest
GET http://localhost:8000/api/accounts/@me
```

```rest
GET http://localhost:8000/api/accounts/@me
Authorization: Token abc
```

```rest
GET http://localhost:8000/api/accounts/@me
Authorization: Bearer invalidToken
```
